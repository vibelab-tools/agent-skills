package com.codex.refactor.smell;

public enum BadSmell {
    MYSTERIOUS_NAME("3.1", "mysterious-name", "Mysterious Name"),
    DUPLICATED_CODE("3.2", "duplicated-code", "Duplicated Code"),
    LONG_FUNCTION("3.3", "long-function", "Long Function"),
    LONG_PARAMETER_LIST("3.4", "long-parameter-list", "Long Parameter List"),
    GLOBAL_DATA("3.5", "global-data", "Global Data"),
    MUTABLE_DATA("3.6", "mutable-data", "Mutable Data"),
    DIVERGENT_CHANGE("3.7", "divergent-change", "Divergent Change"),
    SHOTGUN_SURGERY("3.8", "shotgun-surgery", "Shotgun Surgery"),
    FEATURE_ENVY("3.9", "feature-envy", "Feature Envy"),
    DATA_CLUMPS("3.10", "data-clumps", "Data Clumps"),
    PRIMITIVE_OBSESSION("3.11", "primitive-obsession", "Primitive Obsession"),
    REPEATED_SWITCHES("3.12", "repeated-switches", "Repeated Switches"),
    LOOPS("3.13", "loops", "Loops"),
    LAZY_ELEMENT("3.14", "lazy-element", "Lazy Element"),
    SPECULATIVE_GENERALITY("3.15", "speculative-generality", "Speculative Generality"),
    TEMPORARY_FIELD("3.16", "temporary-field", "Temporary Field"),
    MESSAGE_CHAINS("3.17", "message-chains", "Message Chains"),
    MIDDLE_MAN("3.18", "middle-man", "Middle Man"),
    INSIDER_TRADING("3.19", "insider-trading", "Insider Trading"),
    LARGE_CLASS("3.20", "large-class", "Large Class"),
    ALTERNATIVE_CLASSES_WITH_DIFFERENT_INTERFACES(
            "3.21",
            "alternative-classes-with-different-interfaces",
            "Alternative Classes with Different Interfaces"
    ),
    DATA_CLASS("3.22", "data-class", "Data Class"),
    REFUSED_BEQUEST("3.23", "refused-bequest", "Refused Bequest"),
    COMMENTS("3.24", "comments", "Comments");

    private final String chapter;
    private final String id;
    private final String englishName;

    BadSmell(String chapter, String id, String englishName) {
        this.chapter = chapter;
        this.id = id;
        this.englishName = englishName;
    }

    public String chapter() {
        return chapter;
    }

    public String id() {
        return id;
    }

    public String englishName() {
        return englishName;
    }

}
