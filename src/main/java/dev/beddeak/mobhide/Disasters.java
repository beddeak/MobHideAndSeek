package dev.beddeak.mobhide;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 관리자가 일으키는 재해 이벤트. 빨간 경고 3초 뒤 시작하며 플레이어와 AI 몹에 똑같이 작용해 정체가 드러나지 않습니다.
 * 피해·블록 변화·남는 엔티티 없이 속도·입자·소리만 쓰므로 정리할 것이 없고, 경기가 끝나면 clear()로 바로 멈춥니다.
 * 피해 취소는 MobHidePlugin.onDamage가 참가자와 AI 모두에 적용하므로 낙하 피해도 생기지 않습니다.
 */
final class Disasters {
    enum Kind {
        TORNADO("토네이도", "바람에 휩쓸립니다. 멀리 벗어나세요!"),
        METEOR("유성우", "불덩이가 떨어집니다. 연기 나는 자리를 피하세요!"),
        QUAKE("지진", "땅이 흔들립니다. 균형을 잡으세요!"),
        LIGHTNING("낙뢰", "번개가 내리칩니다. 한곳에 머물지 마세요!");

        final String label;
        final String hint;

        Kind(String label, String hint) { this.label = label; this.hint = hint; }

        /** 영문 이름 또는 한글 이름. 모르는 값은 null. */
        static Kind parse(String text) {
            if (text == null) return null;
            String key = text.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) if (kind.name().toLowerCase(Locale.ROOT).equals(key) || kind.label.equals(key)) return kind;
            return switch (key) {
                case "earthquake" -> QUAKE;
                case "meteors", "meteor-shower" -> METEOR;
                case "thunder" -> LIGHTNING;
                default -> null;
            };
        }

        static Kind random(RandomGenerator random) { return values()[random.nextInt(values().length)]; }
    }

    /** 경기장 정보. 몸(bodies)은 관전자가 아닌 참가자와 살아 있는 AI이며, 관객(audience)은 접속 중인 참가자입니다. */
    interface Stage {
        World world();
        Location randomSpot();
        boolean inside(Location location);
        List<Entity> bodies();
        List<Player> audience();
    }

    private interface Event { boolean tick(long now); }

    static final int WARN_TICKS = 60;
    static final int TORNADO_TICKS = 260, METEOR_TICKS = 220, QUAKE_TICKS = 180, LIGHTNING_TICKS = 200;
    static final double TORNADO_RADIUS = 4.5;

    private final Stage stage;
    private final RandomGenerator random;
    private long now;
    private Kind pending;
    private long startAt;
    private Kind current;
    private Event event;

    Disasters(Stage stage, RandomGenerator random) {
        this.stage = stage;
        this.random = random;
    }

    boolean busy() { return pending != null || event != null; }

    /** 예고 중이거나 진행 중인 재해. 없으면 null. */
    Kind current() { return current; }

    /** 빨간 경고를 띄우고 3초 뒤 시작합니다. 이미 예고·진행 중이면 false. */
    boolean trigger(Kind kind) {
        if (busy()) return false;
        pending = kind;
        current = kind;
        startAt = now + WARN_TICKS;
        title(Component.text("무언가가 다가옵니다...", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text("· · ·", NamedTextColor.RED), 3000);
        chat("§4§l⚠ 무언가가 다가옵니다...");
        sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
        sound(Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.7f);
        return true;
    }

    /** 매 틱 호출. 경기 중이 아닐 때는 부르지 않습니다. */
    void tick() {
        now++;
        if (pending != null) {
            if (now < startAt) {
                if ((startAt - now) % 20 == 0) sound(Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.7f);
                return;
            }
            Kind kind = pending;
            pending = null;
            event = begin(kind);
            if (event == null) { current = null; chat("§7경기장에 재해를 일으킬 자리가 없어 취소했습니다."); return; }
            title(Component.text(kind.label + "!", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(kind.hint, NamedTextColor.GRAY), 2000);
            chat("§c§l" + kind.label + "! §7" + kind.hint);
            sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
        }
        if (event != null && !event.tick(now)) { event = null; current = null; }
    }

    void clear() {
        pending = null;
        event = null;
        current = null;
    }

    private Event begin(Kind kind) {
        return switch (kind) {
            case TORNADO -> { Location spot = stage.randomSpot(); yield spot == null ? null : new Tornado(spot); }
            case METEOR -> new MeteorShower();
            case QUAKE -> new Quake();
            case LIGHTNING -> new Lightning();
        };
    }

    private void title(Component main, Component sub, int stayMillis) {
        Title title = Title.title(main, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMillis), Duration.ofMillis(400)));
        for (Player player : stage.audience()) player.showTitle(title);
    }

    private void chat(String message) { for (Player player : stage.audience()) player.sendMessage(message); }

    private void sound(Sound sound, float volume, float pitch) {
        for (Player player : stage.audience()) player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** 폭발·낙뢰처럼 한 점에서 밀어내는 힘. 가까울수록 세게, 중심에 있으면 임의 방향으로 날립니다. */
    private void blast(Location at, double radius, double push, double pushNear, double up, double upNear) {
        for (Entity body : stage.bodies()) {
            Location pos = body.getLocation();
            if (pos == null || pos.getWorld() != at.getWorld()) continue;
            double dx = pos.getX() - at.getX(), dz = pos.getZ() - at.getZ(), dy = pos.getY() - at.getY();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius || Math.abs(dy) > 3) continue;
            double near = 1 - d / radius;
            if (d < 0.2) { double a = random.nextDouble() * Math.PI * 2; dx = Math.cos(a); dz = Math.sin(a); d = 1; }
            body.setVelocity(new Vector(dx / d * (push + pushNear * near), up + upNear * near, dz / d * (push + pushNear * near)));
        }
    }

    /** 경기장 안을 천천히 가로지르는 구름 기둥. 반경 안의 몸을 끌어올려 돌리고, 지붕 높이쯤에서 놓아 떨어뜨립니다. */
    private final class Tornado implements Event {
        private final Location center;
        private final long until;
        private double heading;

        Tornado(Location spot) {
            center = spot.clone();
            until = now + TORNADO_TICKS;
            heading = random.nextDouble() * Math.PI * 2;
        }

        public boolean tick(long t) {
            Location next = center.clone().add(Math.cos(heading) * 0.07, 0, Math.sin(heading) * 0.07);
            if (stage.inside(next)) { center.setX(next.getX()); center.setZ(next.getZ()); }
            else heading += Math.PI + (random.nextDouble() - 0.5) * 1.6;
            World world = stage.world();
            for (int k = 0; k < 10; k++) {
                double r = 0.5 + k * 0.35, a = t * 0.45 + k * 0.8;
                world.spawnParticle(Particle.CLOUD, center.getX() + Math.cos(a) * r, center.getY() + k * 0.9, center.getZ() + Math.sin(a) * r, 1, 0.08, 0.08, 0.08, 0.0);
            }
            if (t % 8 == 0) world.spawnParticle(Particle.SMALL_GUST, center.getX(), center.getY() + 0.4, center.getZ(), 4, 0.9, 0.2, 0.9, 0.0);
            if (t % 15 == 0) world.playSound(center, Sound.ENTITY_BREEZE_IDLE_AIR, 2.5f, 0.6f);
            if (t % 45 == 0) world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);
            for (Entity body : stage.bodies()) {
                Location at = body.getLocation();
                if (at == null || at.getWorld() != center.getWorld()) continue;
                double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ(), dy = at.getY() - center.getY();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > TORNADO_RADIUS || dy < -1.5 || dy > 11) continue;
                double nx = d < 1e-3 ? 1 : dx / d, nz = d < 1e-3 ? 0 : dz / d;
                double pull = d > 1.2 ? 0.14 : 0.0;
                double up = dy < 7 ? 0.5 : -0.05;
                body.setVelocity(new Vector(-nz * 0.33 - nx * pull, up, nx * 0.33 - nz * pull));
            }
            return t < until;
        }
    }

    /** 하늘에서 떨어지는 불덩이. 떨어질 자리에 연기가 먼저 나고, 닿으면 폭발 입자와 함께 주변을 날립니다. */
    private final class MeteorShower implements Event {
        private record Meteor(Location start, Location target, long impactAt) {}
        private static final int FLIGHT_TICKS = 32;
        private final long until = now + METEOR_TICKS;
        private final List<Meteor> flying = new ArrayList<>();

        public boolean tick(long t) {
            World world = stage.world();
            if (t < until && t % 22 == 0) {
                Location target = stage.randomSpot();
                if (target != null) {
                    Location start = target.clone().add((random.nextDouble() - 0.5) * 20, 28, (random.nextDouble() - 0.5) * 20);
                    flying.add(new Meteor(start, target.clone(), t + FLIGHT_TICKS));
                    world.playSound(target, Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.5f);
                }
            }
            for (Iterator<Meteor> it = flying.iterator(); it.hasNext(); ) {
                Meteor meteor = it.next();
                if (t >= meteor.impactAt()) { impact(world, meteor.target()); it.remove(); continue; }
                double f = 1 - (meteor.impactAt() - t) / (double) FLIGHT_TICKS;
                double x = meteor.start().getX() + (meteor.target().getX() - meteor.start().getX()) * f;
                double y = meteor.start().getY() + (meteor.target().getY() - meteor.start().getY()) * f;
                double z = meteor.start().getZ() + (meteor.target().getZ() - meteor.start().getZ()) * f;
                world.spawnParticle(Particle.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 2, 0.2, 0.2, 0.2, 0.01);
                if (t % 2 == 0) world.spawnParticle(Particle.LAVA, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.SMOKE, meteor.target().getX(), meteor.target().getY() + 0.3, meteor.target().getZ(), 3, 0.5, 0.1, 0.5, 0.0);
            }
            return t < until || !flying.isEmpty();
        }

        private void impact(World world, Location target) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 1);
            world.spawnParticle(Particle.LAVA, target, 25, 1.2, 0.5, 1.2, 0.0);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.75f);
            blast(target, 4.5, 0.45, 1.0, 0.6, 0.4);
        }
    }

    /** 모두가 0.3초마다 임의 방향으로 비틀거리고 발밑에 먼지가 일어납니다. */
    private final class Quake implements Event {
        private final long until = now + QUAKE_TICKS;

        public boolean tick(long t) {
            World world = stage.world();
            if (t % 20 == 0) sound(Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.35f);
            if (t % 6 == 0) for (Entity body : stage.bodies()) {
                Location pos = body.getLocation();
                if (pos == null) continue;
                double a = random.nextDouble() * Math.PI * 2, s = 0.18 + random.nextDouble() * 0.14;
                body.setVelocity(new Vector(Math.cos(a) * s, 0.16, Math.sin(a) * s));
                world.spawnParticle(Particle.DUST_PLUME, pos.getX(), pos.getY() + 0.1, pos.getZ(), 6, 0.5, 0.1, 0.5, 0.0);
            }
            if (t % 4 == 0) {
                Location crack = stage.randomSpot();
                if (crack != null) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, crack.getX(), crack.getY() + 0.2, crack.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
                    world.spawnParticle(Particle.DUST_PLUME, crack, 8, 0.8, 0.1, 0.8, 0.0);
                }
            }
            return t < until;
        }
    }

    /** 시각 효과용 번개(불·피해 없음)가 0.9초마다 떨어집니다. 절반은 플레이어든 AI든 누군가 근처를 노립니다. */
    private final class Lightning implements Event {
        private final long until = now + LIGHTNING_TICKS;

        public boolean tick(long t) {
            if (t % 18 != 0) return t < until;
            World world = stage.world();
            List<Entity> bodies = stage.bodies();
            Location target = null;
            if (!bodies.isEmpty() && random.nextInt(2) == 0) {
                Location pos = bodies.get(random.nextInt(bodies.size())).getLocation();
                if (pos != null) target = pos.clone().add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
            }
            if (target == null) target = stage.randomSpot();
            if (target == null) return t < until;
            world.strikeLightningEffect(target);
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getX(), target.getY() + 0.5, target.getZ(), 30, 1.2, 1.0, 1.2, 0.0);
            blast(target, 3.0, 0.3, 0.4, 0.8, 0.3);
            return t < until;
        }
    }
}
