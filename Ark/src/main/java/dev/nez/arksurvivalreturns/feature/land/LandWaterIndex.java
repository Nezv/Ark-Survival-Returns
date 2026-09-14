package dev.nez.arksurvivalreturns.feature.land;

import java.util.*;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Incremental surface sampling. Every lookup uses getChunkNow; missing terrain stays unknown. */
public final class LandWaterIndex {
    private static final Map<ServerLevel, LandWaterIndex> WORLDS = new WeakHashMap<>();
    private static final class Samples {
        final List<BlockPos> water = new ArrayList<>();
        int cursor;
        long expires;
    }
    private final Map<Long, Samples> chunks = new HashMap<>();
    private final Deque<Long> pending = new ArrayDeque<>();
    private long budgetTick = Long.MIN_VALUE, requestWindow = Long.MIN_VALUE;
    private int probes, requests;
    public static LandWaterIndex get(ServerLevel world) { return WORLDS.computeIfAbsent(world, w -> new LandWaterIndex()); }
    public static void clear() { WORLDS.clear(); }
    public int probesThisTick() { return probes; }
    private boolean reserve(ServerLevel world, int count) {
        if (budgetTick != world.getGameTime()) { budgetTick = world.getGameTime(); probes = 0; }
        if (probes + count > Config.LAND_PROBES.get()) return false;
        probes += count; return true;
    }
    public void request(ServerLevel world, BlockPos pos, int radius) {
        long window = world.getGameTime()/100;
        if (requestWindow != window) { requestWindow = window; requests = 0; }
        if (requests >= 8) return;
        requests++;
        var nearby = new ArrayList<ChunkPos>();
        for (int x=(pos.getX()-radius)>>4; x<=(pos.getX()+radius)>>4; x++)
            for (int z=(pos.getZ()-radius)>>4; z<=(pos.getZ()+radius)>>4; z++) nearby.add(new ChunkPos(x,z));
        nearby.sort(Comparator.comparingDouble(c -> {
            double dx=c.x()*16.0+8-pos.getX(), dz=c.z()*16.0+8-pos.getZ(); return dx*dx+dz*dz;
        }));
        for (var c:nearby) {
            if (world.getChunkSource().getChunkNow(c.x(),c.z())==null) continue;
            long key=key(c.x(),c.z()); var sample=chunks.get(key);
            if (sample != null && (sample.cursor < 16 || sample.expires > world.getGameTime())) continue;
            if (pending.size()>=256 || chunks.size()>=2048 && sample==null) break;
            chunks.put(key,new Samples()); pending.addLast(key);
        }
    }
    public void tick(ServerLevel world) {
        // A column costs at most one height read, sixteen patch cells and one sky-height read.
        reserve(world,0);
        while (!pending.isEmpty() && probes+18<=Config.LAND_PROBES.get()/2 && reserve(world,18)) {
            long key=pending.peekFirst(); var s=chunks.get(key); int cx=chunkX(key), cz=chunkZ(key);
            var chunk=world.getChunkSource().getChunkNow(cx,cz);
            if (s==null || chunk==null) { pending.removeFirst(); chunks.remove(key); continue; }
            int phase=(int)((world.getGameTime()/1200)%4);
            int x=cx*16+(s.cursor%4)*4+phase, z=cz*16+(s.cursor/4)*4+phase;
            int y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15);
            var p=new BlockPos(x,y,z);
            if (patch(world,p)) s.water.add(p);
            pending.removeFirst();
            if (++s.cursor>=16) s.expires=world.getGameTime()+(s.water.isEmpty()?200:Config.LAND_RECHECK.get());
            else pending.addLast(key); // Round-robin columns across pending areas.
        }
        if (world.getGameTime()%200==0) chunks.entrySet().removeIf(e -> {
            long key=e.getKey(); int cx=chunkX(key), cz=chunkZ(key);
            return e.getValue().cursor>=16 &&
                    (e.getValue().expires<=world.getGameTime() || world.getChunkSource().getChunkNow(cx,cz)==null);
        });
    }
    /** Also used when a known shore changes; never trusts biome names or waterlogged blocks. */
    public Boolean observe(ServerLevel world, BlockPos pos) {
        if (!loadedPatch(world,pos) || !reserve(world,18)) return null;
        boolean good=patch(world,pos);
        if (good) {
            long key=key(pos.getX()>>4,pos.getZ()>>4);
            if(!chunks.containsKey(key)&&chunks.size()>=2048)return true;
            var s=chunks.computeIfAbsent(key,k -> { var fresh=new Samples(); fresh.cursor=16; return fresh; });
            if (!s.water.contains(pos)) { if (s.water.size()>=32) s.water.removeFirst(); s.water.add(pos.immutable()); }
            s.expires=world.getGameTime()+Config.LAND_RECHECK.get();
        } else for (var s:chunks.values()) s.water.removeIf(p -> p.equals(pos));
        return good;
    }
    private static boolean loadedPatch(ServerLevel world, BlockPos pos) {
        for (int x=-1;x<=2;x++) for (int z=-1;z<=2;z++)
            if (world.getChunkSource().getChunkNow((pos.getX()+x)>>4,(pos.getZ()+z)>>4)==null) return false;
        return true;
    }
    private static boolean patch(ServerLevel world, BlockPos pos) {
        if (!loadedPatch(world,pos) || !world.canSeeSky(pos.above())) return false;
        boolean[][] water=new boolean[4][4];
        for (int x=0;x<4;x++) for (int z=0;z<4;z++) water[x][z]=world.getBlockState(pos.offset(x-1,0,z-1)).is(Blocks.WATER);
        if (!water[1][1]) return false;
        boolean[][] seen=new boolean[4][4]; var queue=new ArrayDeque<Integer>(); queue.add(5); seen[1][1]=true; int connected=0;
        while (!queue.isEmpty()) {
            int point=queue.removeFirst(), x=point/4,z=point%4; connected++;
            for (int[] direction: new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int nx=x+direction[0],nz=z+direction[1];
                if(nx>=0&&nx<4&&nz>=0&&nz<4&&water[nx][nz]&&!seen[nx][nz]) {seen[nx][nz]=true;queue.add(nx*4+nz);}
            }
        }
        if (connected<Config.LAND_WATER_PATCH.get()) return false;
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) if(seen[x][z]&&seen[x+1][z]&&seen[x][z+1]&&seen[x+1][z+1]) return true;
        return false;
    }
    public List<BlockPos> near(ServerLevel world, BlockPos pos, int radius) {
        var result=new ArrayList<BlockPos>();
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)
            for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++) {
                var s=chunks.get(key(x,z));
                if(s==null||s.cursor>=16&&s.expires<=world.getGameTime()||world.getChunkSource().getChunkNow(x,z)==null) continue;
                for(var p:s.water) if(Math.abs(p.getY()-pos.getY())<=6 && LandHabitatData.distanceSqr(p,pos)<(double)radius*radius) result.add(p);
            }
        return result.stream().distinct().sorted(Comparator.comparingDouble(p -> LandHabitatData.distanceSqr(p,pos))).limit(8).toList();
    }

    // ChunkPos is a record in Minecraft 26.2 and no longer exposes its old packed-long helpers.
    // Keep the index key stable and reversible for the full signed int chunk-coordinate range.
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    private static int chunkX(long key) { return (int)(key >> 32); }
    private static int chunkZ(long key) { return (int)key; }

    private LandWaterIndex() {}
}
