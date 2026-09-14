package dev.nez.arksurvivalreturns.feature.land;

import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import dev.nez.arksurvivalreturns.client.LandHabitatSymbols;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandSymbolsTest {
    @Test void runtimePixelsMatchBothAuthoredSvgAssets() throws Exception {
        check("herbivore",LandHabitatSymbols.HERBIVORE,"#93be8b","#e2f2cc");
        check("carnivore",LandHabitatSymbols.CARNIVORE,"#e48e72","#fff4d8");
    }
    private void check(String name,List<String> rows,String body,String detail)throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var xml=factory.newDocumentBuilder().parse(Path.of("docs/assets/land-"+name+"-habitat.svg").toFile());
        char[][] actual=new char[9][9];for(var row:actual)java.util.Arrays.fill(row,'0');
        var pixels=xml.getElementsByTagName("rect");
        for(int i=0;i<pixels.getLength();i++){
            var p=(org.w3c.dom.Element)pixels.item(i);int x=Integer.parseInt(p.getAttribute("x")),y=Integer.parseInt(p.getAttribute("y"));
            String fill=p.getAttribute("fill");assertTrue(fill.equals(body)||fill.equals(detail));
            assertEquals('0',actual[y][x]);actual[y][x]=fill.equals(body)?'1':'2';
        }
        for(int y=0;y<9;y++)assertEquals(rows.get(y),new String(actual[y]));
    }
}
