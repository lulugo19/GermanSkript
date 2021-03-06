package germanskript

import java.util.*

val präpositionsFälle = mapOf<String, EnumSet<Kasus>>(
    // Präpositionen Genitiv
    "angesichts" to EnumSet.of(Kasus.GENITIV),
    "anhand" to EnumSet.of(Kasus.GENITIV),
    "anlässlich" to EnumSet.of(Kasus.GENITIV),
    "anstatt" to EnumSet.of(Kasus.GENITIV),
    "anstelle" to EnumSet.of(Kasus.GENITIV),
    "aufgrund" to EnumSet.of(Kasus.GENITIV),
    "außerhalb" to EnumSet.of(Kasus.GENITIV),
    "beiderseits" to EnumSet.of(Kasus.GENITIV),
    "bezüglich" to EnumSet.of(Kasus.GENITIV),
    "diesseits" to EnumSet.of(Kasus.GENITIV),
    "infolge" to EnumSet.of(Kasus.GENITIV),
    "innerhalb" to EnumSet.of(Kasus.GENITIV),
    "jenseits" to EnumSet.of(Kasus.GENITIV),
    "längs" to EnumSet.of(Kasus.GENITIV),
    "links" to EnumSet.of(Kasus.GENITIV),
    "mithilfe" to EnumSet.of(Kasus.GENITIV),
    "oberhalb" to EnumSet.of(Kasus.GENITIV),
    "rechts" to EnumSet.of(Kasus.GENITIV),
    "unterhalb" to EnumSet.of(Kasus.GENITIV),
    "statt" to EnumSet.of(Kasus.GENITIV),
    "südlich" to EnumSet.of(Kasus.GENITIV),
    "trotz" to EnumSet.of(Kasus.GENITIV),
    "ungeachtet" to EnumSet.of(Kasus.GENITIV),
    "unweit" to EnumSet.of(Kasus.GENITIV),
    "während" to EnumSet.of(Kasus.GENITIV),
    "wegen" to EnumSet.of(Kasus.GENITIV),
    "westlich" to EnumSet.of(Kasus.GENITIV),

    // Präpositionen Dativ
    "aus" to EnumSet.of(Kasus.DATIV),
    "ab" to EnumSet.of(Kasus.DATIV),
    "außer" to EnumSet.of(Kasus.DATIV),
    "bei" to EnumSet.of(Kasus.DATIV),
    "binnen" to EnumSet.of(Kasus.DATIV),
    "entgegen" to EnumSet.of(Kasus.DATIV),
    "entsprechend" to EnumSet.of(Kasus.DATIV),
    "gegenüber" to EnumSet.of(Kasus.DATIV),
    "gemäß" to EnumSet.of(Kasus.DATIV),
    "mit" to EnumSet.of(Kasus.DATIV),
    "nach" to EnumSet.of(Kasus.DATIV),
    "nahe" to EnumSet.of(Kasus.DATIV),
    "samt" to EnumSet.of(Kasus.DATIV),
    "seit" to EnumSet.of(Kasus.DATIV),
    "von" to EnumSet.of(Kasus.DATIV),
    "zu" to EnumSet.of(Kasus.DATIV),
    "zum" to EnumSet.of(Kasus.DATIV),
    "zur" to EnumSet.of(Kasus.DATIV),
    "zufolge" to EnumSet.of(Kasus.DATIV),
    "zuliebe" to EnumSet.of(Kasus.DATIV),
    "im" to EnumSet.of(Kasus.DATIV),
    "unterm" to EnumSet.of(Kasus.DATIV),
    "überm" to EnumSet.of(Kasus.DATIV),
    "hinterm" to EnumSet.of(Kasus.DATIV),

    // Präpositionen Akkusativ
    "für" to EnumSet.of(Kasus.AKKUSATIV),
    "um" to EnumSet.of(Kasus.AKKUSATIV),
    "durch" to EnumSet.of(Kasus.AKKUSATIV),
    "entlang" to EnumSet.of(Kasus.AKKUSATIV),
    "gegen" to EnumSet.of(Kasus.AKKUSATIV),
    "ohne" to EnumSet.of(Kasus.AKKUSATIV),
    "wider" to EnumSet.of(Kasus.AKKUSATIV),
    "ins" to EnumSet.of(Kasus.AKKUSATIV),
    "durchs" to EnumSet.of(Kasus.AKKUSATIV),
    "fürs" to EnumSet.of(Kasus.AKKUSATIV),

    // Präpositionen Dativ + Akkusativ
    "an" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "auf" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "hinter" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "in" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "neben" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "über" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "unter" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "vor" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "zwischen" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV),
    "an" to EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV)
)