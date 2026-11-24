package org.Cry_Legende.sHCollectibles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.util.BoundingBox;

import java.util.Set;

/** Ce qu'un bloc change pour quelqu'un qui marche, d'après sa boîte de collision. */
final class Blocs {

    /** Rien à heurter. */
    static final byte VIDE = 0;
    /** Plancher dans la case (dalle basse, neige, tête, pot). */
    static final byte BAS = 1;
    static final byte PLEIN = 2;
    /** Plus haut qu'un bloc : on ne saute pas dessus (barrière, muret). */
    static final byte HAUT = 3;
    static final byte GRIMPE = 4;
    static final byte EAU = 5;
    /** Porte ou portillon fermé. */
    static final byte PORTE = 6;
    /** Escalier posé à l'endroit : on tient dedans et dessus. */
    static final byte ESC = 7;
    /** Trappe fermée en haut de sa case : plancher, et passage si on l'ouvre. */
    static final byte TRAPPE = 8;
    /** Seulement le haut de la case (dalle haute) : plancher au-dessus, et passage pour qui marche sur une dalle basse. */
    static final byte TOIT = 9;
    /** Feuilles : on y marche comme sur un bloc plein, mais un chemin par les arbres coûte plus. */
    static final byte FEUILLE = 10;

    /** Largeur d'un joueur (0,6) centrée dans la case. */
    private static final double EDGE = 0.2;

    private Blocs() {
    }

    /** Fil principal : la boîte de collision de certains blocs se lit dans le monde. */
    static byte of(BlockData d, Location at, Set<Material> open, Set<Material> closed) {
        Material m = d.getMaterial();
        if (closed.contains(m)) {
            return PLEIN;
        }
        if (open.contains(m) || m.isAir()) {
            return VIDE;
        }
        if (m == Material.LAVA) {
            return PLEIN;
        }
        if (Tag.CLIMBABLE.isTagged(m)) {
            return GRIMPE;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return FEUILLE;
        }
        if (d instanceof TrapDoor t) {
            return t.isOpen() ? VIDE : t.getHalf() == Bisected.Half.BOTTOM ? BAS : TRAPPE;
        }
        if (d instanceof Door t) {
            return t.isOpen() ? VIDE : PORTE;
        }
        if (d instanceof Gate t) {
            return t.isOpen() ? VIDE : PORTE;
        }
        double top = 0;
        double bottom = 2;
        for (BoundingBox b : d.getCollisionShape(at).getBoundingBoxes()) {
            if (b.getMaxX() > EDGE && b.getMinX() < 1 - EDGE && b.getMaxZ() > EDGE && b.getMinZ() < 1 - EDGE) {
                top = Math.max(top, b.getMaxY());
                bottom = Math.min(bottom, b.getMinY());
            }
        }
        if (top <= EDGE) {
            return wet(d, m) ? EAU : VIDE;
        }
        if (d instanceof Stairs s && s.getHalf() == Bisected.Half.BOTTOM) {
            return ESC;
        }
        if (bottom >= 0.5) {
            return TOIT;
        }
        return top <= 0.6 ? BAS : top <= 1 ? PLEIN : HAUT;
    }

    private static boolean wet(BlockData d, Material m) {
        if (d instanceof Waterlogged w && w.isWaterlogged()) {
            return true;
        }
        return switch (m) {
            case WATER, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> false;
        };
    }
}
