package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.client;

/**
 * One scanned spawn position.
 *
 * @param x          block X of the position a mob would occupy
 * @param y          block Y of the position a mob would occupy (the marker is drawn on its floor)
 * @param z          block Z of the position a mob would occupy
 * @param surfaceY   absolute world Y of the floor the mob would stand on — taken from the
 *                   collision shape of the block below, so markers sit flush on slabs and
 *                   farmland instead of floating at full-block height
 * @param spawnsNow  true when the light test passes regardless of the game's roll — mobs spawn
 *                   here right now; false when it only passes once it gets darker
 * @param spiderRoom true when a spider's wider hitbox also fits (relevant below Y=0, where this
 *                   pack turns every naturally spawned spider into a cave spider)
 */
public record SpawnMarker(int x, int y, int z, float surfaceY, boolean spawnsNow, boolean spiderRoom) {
}
