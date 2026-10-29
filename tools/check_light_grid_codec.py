"""Round-trip and hostile-input checks for the offline palette prototype."""
import random
from light_grid_codec import encode,decode


def main():
    randomizer=random.Random(7)
    for w,h,d in [(1,1,1),(5,3,7),(48,16,48),(80,16,80)]:
        n=w*h*d
        for choices in [1,2,4,16]:
            values=[randomizer.randrange(choices) for _ in range(n)]
            encoded,_=encode(values,w,h,d)
            assert decode(encoded)==(values,w,h,d)
            for cut in [0,9,len(encoded)-1]:
                try:decode(encoded[:cut])
                except ValueError:pass
                else:raise AssertionError('truncation accepted')
            try:decode(encoded+b'\0')
            except ValueError:pass
            else:raise AssertionError('trailing data accepted')
            if w==80:print(f'80x16x80, {choices} values: {len(encoded)} bytes (dense 4-bit: {n//2})')
    for body in [bytes([4]),bytes([0,16]),bytes([1,3]),bytes([2,3,0,1,2,255])]:
        packet=b'ILG1\0\1\0\1\0\1'+body
        try:decode(packet)
        except ValueError:pass
        else:raise AssertionError('malformed mode/palette accepted')
    print('PASS: exact round trips, partial tiles, all modes, malformed and truncated input')


if __name__=='__main__':main()
