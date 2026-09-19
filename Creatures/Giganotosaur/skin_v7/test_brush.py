"""Run: python -m unittest discover -s Creatures/Giganotosaur/skin_v7 -p test_brush.py"""
import unittest
import numpy as np
import build_skin_v7 as painter

class BrushContract(unittest.TestCase):
    def test_full_fill_uses_all_five_shades(self):
        cells=painter.random_brushes(300,300,7319)
        self.assertEqual(set(np.unique(cells)),set(range(5)))
        self.assertGreater(np.mean(cells!=2),.75)
        self.assertLess(np.mean(cells!=2),.85)
        # This is the requested UV brush width, independent of model units.
        expanded=cells.repeat(3,0).repeat(3,1)
        self.assertEqual(expanded.shape,(300,300))
        for y in range(3):
            for x in range(3):
                np.testing.assert_array_equal(expanded[y::3,x::3],cells)

    def test_exact_symmetry_on_odd_and_even_grids(self):
        for width in (27,30):
            for axis in (0,1):
                cells=painter.random_brushes(33,width,21,axis)
                np.testing.assert_array_equal(cells,np.flip(cells,axis))

    def test_seed_reproducibility_and_variation(self):
        a=painter.random_brushes(90,90,101)
        np.testing.assert_array_equal(a,painter.random_brushes(90,90,101))
        self.assertFalse(np.array_equal(a,painter.random_brushes(90,90,102)))

    def test_five_by_five_palette(self):
        cfg=painter.DEFAULTS
        colors=painter.palette(cfg['base_colors'],cfg['shade_offsets'])
        self.assertEqual(colors.shape,(5,5,3))
        for band in colors:
            self.assertEqual(len(np.unique(band,axis=0)),5)
        self.assertTrue(np.all(np.diff(colors[:,2].astype(float).mean(1))<0))

if __name__=='__main__':
    unittest.main()
