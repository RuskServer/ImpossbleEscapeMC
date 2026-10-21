package com.lunar_prototype.impossbleEscapeMC.listener;

/**
 * 作業用エンティティの UUID が、データパックの UUID:[I;0,0,0,110110] (セレクターでは 0-0-0-0-1ae1e) と同じか
 */
public final class DatapackWorkEntityGuardTest {

    public static void main(String[] args) {
        String expected = "00000000-0000-0000-0000-00000001ae1e";
        if (!DatapackWorkEntityGuard.WORK_UUID.toString().equals(expected)) {
            throw new AssertionError("UUID が違う: " + DatapackWorkEntityGuard.WORK_UUID);
        }
        System.out.println("DatapackWorkEntityGuardTest ok");
    }
}
