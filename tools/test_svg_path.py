import unittest
from svg_path import normalize_path
class SvgPathTest(unittest.TestCase):
    def test_compact_arc_flags(self):
        self.assertEqual('A2 2 0 0 1 14 6', normalize_path('A2 2 0 0114 6'))
        self.assertEqual('a.5 .5 0 0 0 -.424 .765', normalize_path('a.5.5 0 0 0-.424.765'))
    def test_repeated_coordinates_and_exponents(self):
        self.assertEqual('M1 2 L3 4 z', normalize_path('M1,2 3,4z'))
        self.assertEqual('m1e-2 -3 h2', normalize_path('m1e-2-3h2'))
    def test_idempotent(self):
        value = normalize_path('m22 4-7.414 7.414-2-2A2 2 0 0114 6')
        self.assertEqual(value, normalize_path(value))
if __name__ == '__main__': unittest.main()
