// Shardbound's Phaser build (P-60): Phaser's own core entry point
// (phaser/src/phaser-core.js) plus the modules the world and battle scenes use:
// tilemaps, particles (rain), tile sprites (the battle backdrop), containers,
// ellipse and rectangle shapes, game object events, Display.Color and Math.Clamp. Physics, Phaser's
// sound system (sound runs through src/audio), video, DOM elements, bitmap text,
// meshes and the other unused game objects stay out: the chunk is about 106 KB
// gzip smaller than the prebuilt bundle. vite.config.ts points `phaser` here
// and sets Phaser's build flags; the types still come from the phaser package.
// A Phaser class or factory used for the first time must be added here;
// phaserBuild.test.ts fails for any Phaser path the world code uses that is missing.
require('phaser-src/polyfills/requestVideoFrame');

var CONST = require('phaser-src/const');
var Extend = require('phaser-src/utils/object/Extend');

var Phaser = {

    Animations: require('phaser-src/animations'),
    BlendModes: require('phaser-src/renderer/BlendModes'),
    Cache: require('phaser-src/cache'),
    Cameras: { Scene2D: require('phaser-src/cameras/2d') },
    Core: require('phaser-src/core'),
    Class: require('phaser-src/utils/Class'),
    Data: require('phaser-src/data'),
    Display: { Masks: require('phaser-src/display/mask'), Color: require('phaser-src/display/color/Color') },
    DOM: require('phaser-src/dom'),
    Events: require('phaser-src/events'),
    Filters: require('phaser-src/filters'),
    Game: require('phaser-src/core/Game'),
    GameObjects: {
        DisplayList: require('phaser-src/gameobjects/DisplayList'),
        GameObjectCreator: require('phaser-src/gameobjects/GameObjectCreator'),
        GameObjectFactory: require('phaser-src/gameobjects/GameObjectFactory'),
        UpdateList: require('phaser-src/gameobjects/UpdateList'),
        Events: require('phaser-src/gameobjects/events'),
        Components: require('phaser-src/gameobjects/components'),
        BuildGameObject: require('phaser-src/gameobjects/BuildGameObject'),
        BuildGameObjectAnimation: require('phaser-src/gameobjects/BuildGameObjectAnimation'),
        GameObject: require('phaser-src/gameobjects/GameObject'),
        Graphics: require('phaser-src/gameobjects/graphics/Graphics'),
        Image: require('phaser-src/gameobjects/image/Image'),
        Layer: require('phaser-src/gameobjects/layer/Layer'),
        Sprite: require('phaser-src/gameobjects/sprite/Sprite'),
        Text: require('phaser-src/gameobjects/text/Text'),
        Container: require('phaser-src/gameobjects/container/Container'),
        Particles: require('phaser-src/gameobjects/particles'),
        TileSprite: require('phaser-src/gameobjects/tilesprite/TileSprite'),
        Shape: require('phaser-src/gameobjects/shape/Shape'),
        Ellipse: require('phaser-src/gameobjects/shape/ellipse/Ellipse'),
        Rectangle: require('phaser-src/gameobjects/shape/rectangle/Rectangle'),
        Factories: {
            Graphics: require('phaser-src/gameobjects/graphics/GraphicsFactory'),
            Image: require('phaser-src/gameobjects/image/ImageFactory'),
            Layer: require('phaser-src/gameobjects/layer/LayerFactory'),
            Sprite: require('phaser-src/gameobjects/sprite/SpriteFactory'),
            Text: require('phaser-src/gameobjects/text/TextFactory'),
            Container: require('phaser-src/gameobjects/container/ContainerFactory'),
            Particles: require('phaser-src/gameobjects/particles/ParticleEmitterFactory'),
            TileSprite: require('phaser-src/gameobjects/tilesprite/TileSpriteFactory'),
            Ellipse: require('phaser-src/gameobjects/shape/ellipse/EllipseFactory'),
            Rectangle: require('phaser-src/gameobjects/shape/rectangle/RectangleFactory'),
            Tilemap: require('phaser-src/tilemaps/TilemapFactory')
        },
        Creators: {
            Graphics: require('phaser-src/gameobjects/graphics/GraphicsCreator'),
            Image: require('phaser-src/gameobjects/image/ImageCreator'),
            Layer: require('phaser-src/gameobjects/layer/LayerCreator'),
            Sprite: require('phaser-src/gameobjects/sprite/SpriteCreator'),
            Text: require('phaser-src/gameobjects/text/TextCreator'),
            Tilemap: require('phaser-src/tilemaps/TilemapCreator')
        }
    },
    Input: require('phaser-src/input'),
    Loader: {
        FileTypes: {
            AnimationJSONFile: require('phaser-src/loader/filetypes/AnimationJSONFile'),
            AtlasJSONFile: require('phaser-src/loader/filetypes/AtlasJSONFile'),
            AudioFile: require('phaser-src/loader/filetypes/AudioFile'),
            AudioSpriteFile: require('phaser-src/loader/filetypes/AudioSpriteFile'),
            HTML5AudioFile: require('phaser-src/loader/filetypes/HTML5AudioFile'),
            ImageFile: require('phaser-src/loader/filetypes/ImageFile'),
            JSONFile: require('phaser-src/loader/filetypes/JSONFile'),
            MultiAtlasFile: require('phaser-src/loader/filetypes/MultiAtlasFile'),
            PluginFile: require('phaser-src/loader/filetypes/PluginFile'),
            ScriptFile: require('phaser-src/loader/filetypes/ScriptFile'),
            SpriteSheetFile: require('phaser-src/loader/filetypes/SpriteSheetFile'),
            TextFile: require('phaser-src/loader/filetypes/TextFile'),
            XMLFile: require('phaser-src/loader/filetypes/XMLFile')
        },
        File: require('phaser-src/loader/File'),
        FileTypesManager: require('phaser-src/loader/FileTypesManager'),
        GetURL: require('phaser-src/loader/GetURL'),
        LoaderPlugin: require('phaser-src/loader/LoaderPlugin'),
        MergeXHRSettings: require('phaser-src/loader/MergeXHRSettings'),
        MultiFile: require('phaser-src/loader/MultiFile'),
        XHRLoader: require('phaser-src/loader/XHRLoader'),
        XHRSettings: require('phaser-src/loader/XHRSettings')
    },
    Math: {
        Between: require('phaser-src/math/Between'),
        Clamp: require('phaser-src/math/Clamp'),
        DegToRad: require('phaser-src/math/DegToRad'),
        FloatBetween: require('phaser-src/math/FloatBetween'),
        RadToDeg: require('phaser-src/math/RadToDeg'),
        Vector2: require('phaser-src/math/Vector2')
    },
    Plugins: require('phaser-src/plugins'),
    Renderer: require('phaser-src/renderer'),
    Scale: require('phaser-src/scale'),
    ScaleModes: require('phaser-src/renderer/ScaleModes'),
    Scene: require('phaser-src/scene/Scene'),
    Scenes: require('phaser-src/scene'),
    Structs: require('phaser-src/structs'),
    Textures: require('phaser-src/textures'),
    Time: require('phaser-src/time'),
    TintModes: require('phaser-src/renderer/TintModes'),
    Tilemaps: require('phaser-src/tilemaps'),
    Tweens: require('phaser-src/tweens')
};

module.exports = Extend(false, Phaser, CONST);

