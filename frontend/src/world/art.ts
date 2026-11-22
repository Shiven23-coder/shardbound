// Art files for the Phaser scenes: sprite sheets, tilesets and effect strips,
// hashed into the build as URLs (P-35). Only the lazily loaded Phaser chunk
// imports this module, so none of it is part of the first page load (P-57).
import { ART_PREFIX } from './content';

const artUrls = import.meta.glob(
  ['../../../content/art/ninja-adventure/eldmere/**/*.png', '../../../content/art/shardbound/*.png'],
  { query: '?url', import: 'default', eager: true },
) as Record<string, string>;

/** The URL of an art file in the build, by its path inside content/art. */
export function artUrl(pathInArt: string): string {
  const url = artUrls[ART_PREFIX + pathInArt];
  if (!url) throw new Error(`No art ${pathInArt} in the build`);
  return url;
}

/** Turns a map's tileset image path ("../art/...") into a build URL. */
export function tilesetUrl(imageInMap: string): string {
  return artUrl(imageInMap.replace(/^\.\.\/art\//, ''));
}
