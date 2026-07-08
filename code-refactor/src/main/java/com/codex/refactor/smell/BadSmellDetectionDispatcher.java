package com.codex.refactor.smell;

import com.codex.refactor.smell.detectors.AlternativeClassesWithDifferentInterfacesBadSmellDetector;
import com.codex.refactor.smell.detectors.CommentsBadSmellDetector;
import com.codex.refactor.smell.detectors.DataClassBadSmellDetector;
import com.codex.refactor.smell.detectors.DataClumpsBadSmellDetector;
import com.codex.refactor.smell.detectors.DivergentChangeBadSmellDetector;
import com.codex.refactor.smell.detectors.DuplicatedCodeBadSmellDetector;
import com.codex.refactor.smell.detectors.FeatureEnvyBadSmellDetector;
import com.codex.refactor.smell.detectors.GlobalDataBadSmellDetector;
import com.codex.refactor.smell.detectors.InsiderTradingBadSmellDetector;
import com.codex.refactor.smell.detectors.LargeClassBadSmellDetector;
import com.codex.refactor.smell.detectors.LazyElementBadSmellDetector;
import com.codex.refactor.smell.detectors.LongFunctionBadSmellDetector;
import com.codex.refactor.smell.detectors.LongParameterListBadSmellDetector;
import com.codex.refactor.smell.detectors.LoopsBadSmellDetector;
import com.codex.refactor.smell.detectors.MessageChainsBadSmellDetector;
import com.codex.refactor.smell.detectors.MiddleManBadSmellDetector;
import com.codex.refactor.smell.detectors.MutableDataBadSmellDetector;
import com.codex.refactor.smell.detectors.MysteriousNameBadSmellDetector;
import com.codex.refactor.smell.detectors.PrimitiveObsessionBadSmellDetector;
import com.codex.refactor.smell.detectors.RefusedBequestBadSmellDetector;
import com.codex.refactor.smell.detectors.RepeatedSwitchesBadSmellDetector;
import com.codex.refactor.smell.detectors.ShotgunSurgeryBadSmellDetector;
import com.codex.refactor.smell.detectors.SpeculativeGeneralityBadSmellDetector;
import com.codex.refactor.smell.detectors.TemporaryFieldBadSmellDetector;

import java.util.ArrayList;
import java.util.List;

public final class BadSmellDetectionDispatcher {
    private final List<BadSmellDetector> detectors;

    public BadSmellDetectionDispatcher(List<BadSmellDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    public static BadSmellDetectionDispatcher standard() {
        return new BadSmellDetectionDispatcher(List.of(
                new MysteriousNameBadSmellDetector(),
                new DuplicatedCodeBadSmellDetector(),
                new LongFunctionBadSmellDetector(),
                new LongParameterListBadSmellDetector(),
                new GlobalDataBadSmellDetector(),
                new MutableDataBadSmellDetector(),
                new DivergentChangeBadSmellDetector(),
                new ShotgunSurgeryBadSmellDetector(),
                new FeatureEnvyBadSmellDetector(),
                new DataClumpsBadSmellDetector(),
                new PrimitiveObsessionBadSmellDetector(),
                new RepeatedSwitchesBadSmellDetector(),
                new LoopsBadSmellDetector(),
                new LazyElementBadSmellDetector(),
                new SpeculativeGeneralityBadSmellDetector(),
                new TemporaryFieldBadSmellDetector(),
                new MessageChainsBadSmellDetector(),
                new MiddleManBadSmellDetector(),
                new InsiderTradingBadSmellDetector(),
                new LargeClassBadSmellDetector(),
                new AlternativeClassesWithDifferentInterfacesBadSmellDetector(),
                new DataClassBadSmellDetector(),
                new RefusedBequestBadSmellDetector(),
                new CommentsBadSmellDetector()
        ));
    }

    public List<BadSmellDetector> detectors() {
        return detectors;
    }

    public List<SmellFinding> detect(SmellAnalysisContext context) {
        List<SmellFinding> findings = new ArrayList<>();
        for (BadSmellDetector detector : detectors) {
            if (detector.isImplemented()) {
                findings.addAll(detector.detect(context));
            }
        }
        return List.copyOf(findings);
    }
}
