package com.lumengrid.recursiveae2patternprovider.menu;

import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Map;

public interface IPlanExplanationMenu {
    Map<AEKey, List<AEKey>> recursiveAE2$getExplanation();
    void recursiveAE2$setExplanation(Map<AEKey, List<AEKey>> explanation);
    Map<AEKey, List<AEKey>> recursiveAE2$getOrigins();
    void recursiveAE2$setOrigins(Map<AEKey, List<AEKey>> origins);
}
