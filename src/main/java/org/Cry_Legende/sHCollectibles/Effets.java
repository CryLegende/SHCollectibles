package org.Cry_Legende.sHCollectibles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Joue les actions d'une trouvaille. Les effets ne sont montrés qu'au joueur et ne sont jamais sauvegardés. */
final class Effets implements Listener {

    private static final float ITEM_SIZE = 0.35f;
    private static final int RISE = 7;
    private static final int FALL = 10;

    private final SHCollectibles plugin;
    private final Set<Entity> shown = new HashSet<>();

    Effets(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    void play(Player p, Collection c, Collection.Item item, Actions a, int found) {
        String[] r = {"joueur", p.getName(), "trouves", Integer.toString(found), "total", Integer.toString(c.total()),
                "restants", Integer.toString(Math.max(0, c.total() - found))};
        for (String line : a.chat()) {
            p.sendMessage(Textes.forPlayer(p, line, r));
        }
        if (!a.title().isEmpty() || !a.subtitle().isEmpty()) {
            p.showTitle(Title.title(Textes.forPlayer(p, a.title(), r), Textes.forPlayer(p, a.subtitle(), r),
                    Title.Times.times(ticks(a.fadeIn()), ticks(a.stay()), ticks(a.fadeOut()))));
        }
        sound(p, a.sound(), p.getLocation());
        for (String command : a.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p,
                    Textes.replace(command, r)));
        }
        Location at = item.center().location();
        if (at == null) {
            return;
        }
        at.setY(item.place().y());
        if (a.hologram() != null) {
            hologram(p, at, a.hologram(), r);
        }
        if (a.items() != null) {
            items(p, at, a.items());
        }
        if (a.firework() != null) {
            firework(p, at, a.firework());
        }
    }

    static void sound(Player p, Actions.Son s, Location at) {
        if (!s.key().isEmpty()) {
            p.playSound(at, s.key(), SoundCategory.MASTER, s.volume(), s.pitch());
        }
    }

    private void hologram(Player p, Location at, Actions.Hologram h, String[] r) {
        Component text = Component.empty();
        for (int i = 0; i < h.lines().size(); i++) {
            text = text.append(i == 0 ? Component.empty() : Component.newline()).append(Textes.forPlayer(p, h.lines().get(i), r));
        }
        Component all = text;
        TextDisplay e = at.getWorld().spawn(at.clone().add(0, h.height(), 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.text(all);
        });
        show(p, e, h.ticks());
    }

    /** Les objets montent puis retombent autour du collectible, animés par le client. */
    private void items(Player p, Location at, Actions.Items it) {
        int[] left = {it.count()};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0]-- <= 0 || !p.isOnline() || p.getWorld() != at.getWorld()) {
                task[0].cancel();
                return;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Material m = it.materials().get(rnd.nextInt(it.materials().size()));
            double angle = rnd.nextDouble(Math.PI * 2);
            float far = 0.5f + rnd.nextFloat() * 0.8f;
            Vector3f end = new Vector3f((float) Math.cos(angle) * far, -0.4f, (float) Math.sin(angle) * far);
            Vector3f top = new Vector3f(end.x * 0.55f, 0.6f + rnd.nextFloat() * 0.5f, end.z * 0.55f);
            Quaternionf turn = new Quaternionf().rotateY((float) angle);
            ItemDisplay e = at.getWorld().spawn(at.clone().add(0, it.height(), 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setItemStack(new ItemStack(m));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(transformation(new Vector3f(), turn, ITEM_SIZE));
            });
            show(p, e, 2 + RISE + FALL);
            glide(e, top, turn, ITEM_SIZE * 2, RISE, 2);
            glide(e, end, turn, ITEM_SIZE, FALL, 2 + RISE);
            sound(p, it.sound(), at);
        }, 0L, it.delay());
    }

    private void glide(ItemDisplay e, Vector3f to, Quaternionf turn, float size, int duration, long after) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) {
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(duration);
                e.setTransformation(transformation(to, turn, size));
            }
        }, after);
    }

    private static Transformation transformation(Vector3f move, Quaternionf turn, float size) {
        return new Transformation(move, turn, new Vector3f(size, size, size), new Quaternionf());
    }

    private void firework(Player p, Location at, Actions.Firework f) {
        Firework e = at.getWorld().spawn(at.clone().add(0, f.height(), 0), Firework.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setSilent(true);
            FireworkMeta meta = d.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(f.type()).withColor(f.colors()).withFade(f.fade()).build());
            d.setFireworkMeta(meta);
        });
        shown.add(e);
        p.showEntity(plugin, e);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) {
                e.detonate();
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> shown.remove(e), 20L);
        }, 2L);
    }

    /** Un feu d'artifice montré à un seul joueur ne blesse personne. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (shown.contains(e.getDamager())) {
            e.setCancelled(true);
        }
    }

    private void show(Player p, Entity e, long ticks) {
        shown.add(e);
        p.showEntity(plugin, e);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            shown.remove(e);
            e.remove();
        }, ticks);
    }

    void clear() {
        for (Entity e : List.copyOf(shown)) {
            e.remove();
        }
        shown.clear();
    }

    private static Duration ticks(int n) {
        return Duration.ofMillis(Math.max(0, n) * 50L);
    }
}
