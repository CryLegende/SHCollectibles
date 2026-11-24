package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Point de couleur des collectibles proches, montré seulement aux joueurs qui ne les ont pas encore. Tout se fait hors du
 * fil principal : une part des joueurs toutes les 50 ms.
 */
final class Particules {

    /** Tête à signaler, prête à être comparée à la position des joueurs. */
    private record Spot(Collection collection, int number, double x, double y, double z, double height, double heightMax,
                        double orbit, Particle.DustOptions dust) {
    }

    private final SHCollectibles plugin;
    private volatile Map<String, Spot[]> spots = Map.of();
    private int now;

    Particules(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    /** Refait la liste des têtes à signaler, par monde. */
    void index(Reglages s) {
        Map<String, List<Spot>> out = new HashMap<>();
        for (Collection c : s.collections().values()) {
            Collection.Particles look = c.particles();
            if (look == null || !c.enabled()) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(look.rgb()), (float) s.particleSize());
            for (Collection.Item i : c.items().values()) {
                Lieu l = i.place();
                out.computeIfAbsent(l.world(), k -> new ArrayList<>()).add(new Spot(c, i.id(), l.x() + 0.5, l.y(), l.z() + 0.5,
                        look.height(), look.heightMax(), look.orbit(), dust));
            }
        }
        Map<String, Spot[]> ready = new HashMap<>();
        out.forEach((world, list) -> ready.put(world, list.toArray(new Spot[0])));
        spots = ready;
    }

    /** Toutes les 50 ms : les joueurs dont c'est le tour. */
    void tick() {
        now++;
        Map<String, Spot[]> all = spots;
        if (all.isEmpty()) {
            return;
        }
        int period = plugin.settings().particleTicks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((p.getEntityId() + now) % period != 0) {
                continue;
            }
            Spot[] here = all.get(p.getWorld().getName());
            Donnees d = here == null ? null : plugin.joueurs().get(p.getUniqueId());
            if (d == null) {
                continue;
            }
            show(p, d, here, p.getLocation(), now / period);
        }
    }

    private void show(Player p, Donnees d, Spot[] here, Location l, int step) {
        double max = plugin.settings().particleDistance();
        int orbit = plugin.settings().orbitSteps();
        for (Spot s : here) {
            double dx = s.x() - l.getX();
            double dz = s.z() - l.getZ();
            if (Math.abs(dx) > max || Math.abs(dz) > max) {
                continue;
            }
            double dy = s.y() - l.getY();
            if (dx * dx + dy * dy + dz * dz > max * max || d.completed().contains(s.collection().id())
                    || d.read(s.collection().id()).contains(s.number())) {
                continue;
            }
            double x = s.x();
            double z = s.z();
            if (s.orbit() > 0) {
                double angle = Math.PI * 2 * (step % orbit) / orbit;
                x += Math.cos(angle) * s.orbit();
                z += Math.sin(angle) * s.orbit();
            }
            double y = s.y() + (s.heightMax() > s.height() ? ThreadLocalRandom.current().nextDouble(s.height(), s.heightMax()) : s.height());
            p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, s.dust());
        }
    }
}
