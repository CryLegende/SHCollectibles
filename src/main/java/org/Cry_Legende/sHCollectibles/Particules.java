package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Point de couleur des collectibles proches, montré seulement aux joueurs qui ne les ont pas encore. */
final class Particules {

    private static final int ORBIT_STEPS = 10;

    private final SHCollectibles plugin;
    private final Map<Integer, Particle.DustOptions> dusts = new HashMap<>();
    private int turn;

    Particules(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    void tick() {
        turn++;
        double max = plugin.settings().particleDistance();
        double max2 = max * max;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Donnees d = plugin.joueurs().get(p.getUniqueId());
            if (d == null) {
                continue;
            }
            Location l = p.getLocation();
            for (Recherche.Found f : plugin.recherche().in(l.getWorld().getName())) {
                Collection c = f.collection();
                Collection.Particles look = c.particles();
                Lieu at = f.item().place();
                double dx = at.x() + 0.5 - l.getX();
                double dy = at.y() - l.getY();
                double dz = at.z() + 0.5 - l.getZ();
                if (look == null || dx * dx + dy * dy + dz * dz > max2 || !c.enabled()
                        || d.completed().contains(c.id()) || d.read(c.id()).contains(f.item().id())) {
                    continue;
                }
                double x = at.x() + 0.5;
                double z = at.z() + 0.5;
                if (look.orbit() > 0) {
                    double angle = Math.PI * 2 * (turn % ORBIT_STEPS) / ORBIT_STEPS;
                    x += Math.cos(angle) * look.orbit();
                    z += Math.sin(angle) * look.orbit();
                }
                double y = at.y() + (look.heightMax() > look.height()
                        ? ThreadLocalRandom.current().nextDouble(look.height(), look.heightMax()) : look.height());
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0,
                        dusts.computeIfAbsent(look.rgb(), rgb -> new Particle.DustOptions(Color.fromRGB(rgb), 1f)));
            }
        }
    }
}
