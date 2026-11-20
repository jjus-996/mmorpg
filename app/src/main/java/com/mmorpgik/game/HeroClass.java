package com.mmorpgik.game;

import static com.mmorpgik.game.Skill.Type.*;

/** Beş sınıf, beş kahraman, beş ruh hayvanı, her sınıfa beş yetenek. */
public enum HeroClass {
    WARRIOR("Savaşçı", "Draegor", "Goredar", "Kurt",
            "Güç, cesaret ve dayanıklılık. Ön safta savaşır.",
            0xFFC8472B, 150, 40, 1.00f, 70f,
            new Skill[]{
                new Skill("Kurt Pençesi", MELEE, 4f, 8, 2.0f, 110f, "Önündeki düşmanlara güçlü pençe darbesi"),
                new Skill("Yer Sarsıntısı", AOE_SELF, 9f, 18, 1.6f, 150f, "Çevrene şok dalgası yayar"),
                new Skill("Kalkan Duvarı", SHIELD, 16f, 15, 0f, 6f, "6 sn hasarı büyük ölçüde azaltır"),
                new Skill("Atılış", DASH, 7f, 10, 1.2f, 190f, "İleri atılır, yoldakilere çarpar"),
                new Skill("Savaş Narası", POWER, 20f, 20, 0f, 8f, "8 sn saldırı gücünü artırır")
            }),
    ROGUE("Gizlenci", "Zhaelric", "Clazheri", "Tilki",
            "Çeviklik, gizlilik ve kurnazlık. Hızlı vurur.",
            0xFF6E5BB0, 100, 60, 1.35f, 65f,
            new Skill[]{
                new Skill("Gölge Hançeri", MELEE, 3f, 6, 1.8f, 90f, "Çok hızlı, kısa menzilli darbe"),
                new Skill("Zehirli Ok", BOLT, 5f, 10, 1.7f, 420f, "Uzağa giden zehirli ok"),
                new Skill("Duman Bombası", AOE_TARGET, 10f, 16, 1.3f, 120f, "Önüne duman bırakır, içindekilere hasar verir"),
                new Skill("Tilki Sıçrayışı", DASH, 6f, 8, 0f, 230f, "Uzun bir sıçrayışla yer değiştirir"),
                new Skill("Gizlilik", HASTE, 18f, 14, 0f, 7f, "7 sn çok hızlı koşarsın")
            }),
    MAGE("Büyücü", "Yraalys", "Asylary", "Baykuş",
            "Bilgelik, büyü ve kadim bilgiler. Uzaktan yıkar.",
            0xFF2FA7C4, 80, 120, 1.55f, 320f,
            new Skill[]{
                new Skill("Ateş Topu", BOLT, 3f, 12, 2.2f, 450f, "Tek hedefe ateş topu"),
                new Skill("Buz Halkası", AOE_SELF, 9f, 22, 1.7f, 170f, "Etrafına buz dalgası yayar"),
                new Skill("Yıldırım", AOE_TARGET, 8f, 20, 2.6f, 100f, "Önüne yıldırım düşürür"),
                new Skill("Büyü Kalkanı", SHIELD, 18f, 25, 0f, 6f, "6 sn büyülü koruma"),
                new Skill("Meteor", AOE_TARGET, 16f, 40, 4.2f, 190f, "Gökten dev bir meteor düşer")
            }),
    PRIEST("Rün Gözcüsü", "Aurelith", "Ethurial", "Beyaz Güvercin",
            "Kutsal rünler, şifa ve ışık. Dostlarını korur.",
            0xFFF0C94A, 110, 110, 1.45f, 300f,
            new Skill[]{
                new Skill("Kutsal Işık", BOLT, 3f, 10, 1.9f, 440f, "Işık huzmesi gönderir"),
                new Skill("Şifa", HEAL, 8f, 25, 0f, 0.45f, "Canının yüzde 45'ini yeniler"),
                new Skill("Arınma Halkası", AOE_SELF, 10f, 20, 1.5f, 160f, "Işık halkası yayar"),
                new Skill("Kutsal Kalkan", SHIELD, 20f, 22, 0f, 7f, "7 sn kutsal koruma"),
                new Skill("Güvercin Fırtınası", AOE_TARGET, 14f, 32, 3.3f, 170f, "Önüne beyaz güvercinler yağdırır")
            }),
    SUMMONER("Çağırıcı", "Sorvalith", "Vorlathis", "Phoenix",
            "Ateş, yeniden doğuş ve çağrı. Yaratıklarla savaşır.",
            0xFFE8702A, 105, 105, 1.50f, 300f,
            new Skill[]{
                new Skill("Alev Kuşu", BOLT, 3f, 11, 2.0f, 440f, "Küçük bir alev kuşu fırlatır"),
                new Skill("Phoenix Çağrısı", SUMMON, 24f, 35, 1.1f, 12f, "12 sn seninle savaşan bir phoenix çağırır"),
                new Skill("Kül Patlaması", AOE_SELF, 9f, 20, 1.6f, 160f, "Etrafında kül ve ateş patlar"),
                new Skill("Diriliş Alevi", HEAL, 10f, 28, 0f, 0.40f, "Canının yüzde 40'ını yeniler"),
                new Skill("Ateş Yağmuru", AOE_TARGET, 15f, 34, 3.4f, 180f, "Önüne ateş yağdırır")
            });

    public final String title;       // sınıf adı
    public final String hero;        // kahraman
    public final String corrupted;   // yozlaşmış boss adı
    public final String spirit;      // ruh hayvanı
    public final String blurb;
    public final int color;
    public final int hpBase;
    public final int mpBase;
    public final float atkScale;
    public final float basicRange;
    public final Skill[] skills;

    HeroClass(String title, String hero, String corrupted, String spirit, String blurb, int color,
              int hpBase, int mpBase, float atkScale, float basicRange, Skill[] skills) {
        this.title = title;
        this.hero = hero;
        this.corrupted = corrupted;
        this.spirit = spirit;
        this.blurb = blurb;
        this.color = color;
        this.hpBase = hpBase;
        this.mpBase = mpBase;
        this.atkScale = atkScale;
        this.basicRange = basicRange;
        this.skills = skills;
    }

    public boolean basicIsRanged() { return basicRange > 200f; }
}
