package com.example.ooad_project.Events;

import com.example.ooad_project.Parasite.Parasite;

public class ParasiteEvent {

    private final Parasite parasite;
    /** When non-null, only attack this plant. When null, attack all vulnerable plants (legacy). */
    private final Integer targetRow;
    private final Integer targetCol;

    public ParasiteEvent(Parasite parasite) {
        this(parasite, null, null);
    }

    public ParasiteEvent(Parasite parasite, Integer targetRow, Integer targetCol) {
        this.parasite = parasite;
        this.targetRow = targetRow;
        this.targetCol = targetCol;
    }

    public Parasite getParasite() {
        return parasite;
    }

    public Integer getTargetRow() { return targetRow; }
    public Integer getTargetCol() { return targetCol; }
    public boolean hasTarget() { return targetRow != null && targetCol != null; }
}
