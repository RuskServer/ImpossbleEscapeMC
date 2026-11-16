package com.lunar_prototype.impossbleEscapeMC.modules.medical;

/** 薬剤を使うAI-2と、包装を開けて処置するIFAKの音を分ける。 */
final class MedicalKitSounds {
    private MedicalKitSounds() {}

    static String step(String id, int heals) {
        if ("ifak".equals(id)) return heals % 2 == 1 ? "unwrap" : "dress";
        return heals % 2 == 1 ? "pills" : "blister";
    }
}
