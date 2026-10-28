package com.lunar_prototype.impossbleEscapeMC.modules.stamina;

/** 満タンから走り、予備を残して歩行回復し、再び走る基準ペースの移動時間。 */
public record StaminaTravelModel(double capacity, double drainPerSecond, double recoveryPerSecond,
                                 double recoveryDelay, double reserve, double sprintSpeed,
                                 double walkSpeed, boolean canSprint) {
    public StaminaTravelModel {
        if (!Double.isFinite(capacity + drainPerSecond + recoveryPerSecond + recoveryDelay + reserve + sprintSpeed + walkSpeed)
                || capacity <= 0 || drainPerSecond <= 0 || recoveryPerSecond <= 0 || recoveryDelay < 0
                || reserve < 0 || reserve >= capacity || sprintSpeed <= 0 || walkSpeed <= 0) {
            throw new IllegalArgumentException("Invalid travel profile");
        }
    }
    public double seconds(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        if (!canSprint || sprintSpeed <= walkSpeed) return distance / walkSpeed;
        double usable = capacity - reserve;
        double runTime = usable / drainPerSecond;
        // 実装の回復条件は待ち時間を「超えた」tick。最大1tickを追加する。
        double restTime = recoveryDelay + .05 + usable / recoveryPerSecond;
        double runDistance = runTime * sprintSpeed;
        double cycleDistance = runDistance + restTime * walkSpeed;
        double cycles = Math.floor(distance / cycleDistance);
        double remaining = distance - cycles * cycleDistance;
        return cycles * (runTime + restTime) + (remaining <= runDistance ? remaining / sprintSpeed
                : runTime + (remaining - runDistance) / walkSpeed);
    }
}
