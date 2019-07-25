package com.pveplands.treasurehunting;

import com.wurmonline.server.Items;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemTemplateCreator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javassist.*;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import mod.sin.lib.Util;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

/**
 * Treasure Hunting may create treasure maps found by digging, mining, fishing,
 * or killing creatures as specified in the config file. Maps can be read by
 * using a compass on them. Once at the right spot, a pickaxe or shovel can be
 * used on the map to dig up the treasure chest. Rewards and monster spawns can
 * be configured.
 *
 * GameMasters may create treasure maps using their wands, teleport to the
 * target location, and use any tool to dig up the treasure.
 *
 * This mod was brought to you by http://pveplands.com
 * Forum post:
 */
public class TreasureHunting implements WurmServerMod, Configurable, Initable, PreInitable, ItemTemplatesCreatedListener, ServerStartedListener {
    private static final Logger logger = Logger.getLogger(getLoggerName(TreasureHunting.class));
    @SuppressWarnings("rawtypes")
    public static String getLoggerName(Class c) { return String.format("%s (v%s)", c.getName(), c.getPackage().getImplementationVersion()); }

    //private static final Random random = new Random();

    private static final TreasureOptions options = new TreasureOptions();
    public static TreasureOptions getOptions() {
        return options;
    }

    public TreasureHunting () {
    }

    public static void checkTreasureGuardian(Creature creature, Item corpse){
        if(Treasuremap.guardians.containsKey(creature.getWurmId())){
            long chestId = Treasuremap.guardians.get(creature.getWurmId());
            if(Treasuremap.guardianCount.containsKey(chestId)){
                int count = Treasuremap.guardianCount.get(chestId);
                count--;
                if(count <= 0){
                    try {
                        Item chest = Items.getItem(Treasuremap.guardians.get(creature.getWurmId()));
                        if(chest != null && chest.isLocked()){
                            Item lock = Items.getItem(chest.getLockId());
                            Server.getInstance().broadCastAction("The treasure's guardians have been defeated and the protection is reduced.", creature, 20);
                            if(options.isDestroyLock()){
                                chest.unlock();
                                chest.setLockId(-10);
                                Items.destroyItem(lock.getWurmId());
                            }else{
                                lock.setQualityLevel(Math.max(1, lock.getQualityLevel()*0.3f));}
                        }
                    } catch (NoSuchItemException e) {
                        e.printStackTrace();
                    }
                }else{
                    Treasuremap.guardianCount.put(chestId, count);
                }
            }
            //Treasuremap.guardians.remove(creature.getWurmId());
        }
    }

    @Override
    public void preInit() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            // Die method description
            CtClass ctString = classPool.get("java.lang.String");
            CtClass[] params1 = new CtClass[]{
                    CtClass.booleanType,
                    ctString,
                    CtClass.booleanType
            };
            String desc1 = Descriptor.ofMethod(CtClass.voidType, params1);

            Util.setReason("Register hook for creature death.");
            CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            replace = "$_ = $proceed($$);"
                    + TreasureHunting.class.getName()+".checkTreasureGuardian(this, corpse);";
            Util.instrumentDescribed(thisClass, ctCreature, "die", desc1, "setRotation", replace);
        } catch (NotFoundException e) {
            e.printStackTrace();
        }

        if (options.isTerraforming()) AddMethodCallsTerraforming();
        if (options.isMining()) AddMethodCallsMining();
        if (options.isFishing()) AddMethodCallsFishing();
        if (options.isHunting()) AddMethodCallsHunting();
        if (options.isWoodcutting()) AddMethodCallsWoodcutting();
        if (options.isForaging()) AddMethodCallsForaging();
        if (options.isArchaeology()) AddMethodCallsInvestigate();
        if (options.isPrayer()) AddMethodCallsPrayer();

        ModActions.init();
    }

    @Override
    public void configure(Properties p) {
        logger.info("Loading configuration.");
        options.configure(p);
        logger.info("Configuration loaded.");
    }

    @Override
    public void onItemTemplatesCreated() {
        try {
            ItemTemplateCreator.createItemTemplate(options.getTreasuremapTemplateId(), "treasure map", "treasure maps", "excellent", "good", "ok", "poor",
                    "An old weathered treasure map with a big X marking a spot. What could you use on it, to get directions?",
                    new short[] { 48, 157, 187 }, (short)640, (short)1, 0, 3024000L, 5, 5, 5, -10,
                    MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY, "model.resource.sheet.", 5f, 500, (byte)33, 20000, true);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Could not create treasure map item.", e);
        }
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(options.setCreatemapAction(new CreateRandomTreasuremapAction()));
        ModActions.registerAction(options.setCreatehereAction(new CreateTreasuremapHereAction()));
        ModActions.registerAction(options.setTeleportAction(new TeleportToTreasureAction()));
        ModActions.registerAction(options.setReloadAction(new ReloadConfigAction()));
        ModActions.registerAction(options.setReadmapAction(new ReadTreasuremapAction()));
        ModActions.registerAction(options.setDigAction(new DigUpTreasureAction()));
        ModActions.registerAction(options.setUnloadAction(new UnloadFromTreasureAction()));
        ModActions.registerAction(options.setChestAction(new SpawnTreasurechestAction()));
        ModActions.registerAction(options.setBehaviours(new TreasureBehaviour()));
    }

    /**
     * Injects calls to our mod whenever a creature dies and there were
     * attackers (i.e. it did not die of old age). This will create treasure
     * maps if applicable.
     */
    private void AddMethodCallsHunting() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Hunting treasure map hook.");
            CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            replace = "if (this.getLatestAttackers().length > 0){" +
                      Treasuremap.class.getName()+".CreateTreasuremap(null, null, null, this);" +
                    "}";
            Util.insertBeforeDeclared(thisClass, ctCreature, "die", replace);

            /*HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature")
                    .getMethod("die", "(Z)V")
                    .insertBefore("{ if (this.getLatestAttackers().length > 0) com.pveplands.treasurehunting.Treasuremap.CreateTreasuremap(null, null, null, this); }");*/
            //com.pveplands.treasurehunting.Treasuremap.debugDeath(this);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add methods to hunting.", e);
        }
    }

    /**
     * Injects calls to mining, whenever rock shards are created during mining
     * a wall, and leveling or flatting a cave floor or ceiling.
     */
    private void AddMethodCallsMining() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Action cave wall hook.");
            CtClass ctAction = classPool.get("com.wurmonline.server.behaviours.Action");
            CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
            // public boolean action(Action act, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, int dir, short action, float counter)
            CtClass ctCaveWallBehaviour = classPool.get("com.wurmonline.server.behaviours.CaveWallBehaviour");
            CtClass[] params1 = {
                    ctAction,
                    ctCreature,
                    ctItem,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.booleanType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.shortType,
                    CtClass.floatType
            };
            String desc1 = Descriptor.ofMethod(CtClass.booleanType, params1);
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(1008), null);";
            Util.instrumentDescribed(thisClass, ctCaveWallBehaviour, "action", desc1, "putItemInfrontof", replace);

            // private boolean flatten(Creature performer, Item source, int tile, int tilex, int tiley, float counter, Action act, int dir) {
            CtMethod tileFlattenMethod = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.CaveTileBehaviour")
                    .getMethod("flatten", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFLcom/wurmonline/server/behaviours/Action;I)Z");

            tileFlattenMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("putItemInfrontof")) {
                        tileFlattenMethod.insertAt(
                                methodCall.getLineNumber() + 1,
                                "{ com.pveplands.treasurehunting.Treasuremap.CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(1008), (com.wurmonline.server.creatures.Creature)null); }"
                        );
                    }
                }
            });

            // Surface mining and tunneling.
            // public static final boolean mine(Action act, Creature performer, Item source, int tilex, int tiley, short action, float counter, int digTilex, int digTiley) {
            CtMethod surfaceMiningMethod = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.TileRockBehaviour")
                    .getDeclaredMethod("mine");

            Util.setReason("Insert surface mining action hook.");
            // public boolean action(Action act, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short action, float counter)
            CtClass ctTileRockBehaviour = classPool.get("com.wurmonline.server.behaviours.TileRockBehaviour");
            CtClass[] params2 = {
                    ctAction,
                    ctCreature,
                    ctItem,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.booleanType,
                    CtClass.intType,
                    CtClass.intType,
                    CtClass.shortType,
                    CtClass.floatType
            };
            String desc2 = Descriptor.ofMethod(CtClass.booleanType, params2);
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(10009), null);";
            Util.instrumentDescribed(thisClass, ctTileRockBehaviour, "action", desc2, "createItem", replace);

            Util.setReason("Insert surface mining mine hook.");
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(10009), null);";
            Util.instrumentDeclared(thisClass, ctTileRockBehaviour, "mine", "createItem", replace);

        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to mining.", e);
        }
    }

    /**
     * Injects a call to fishing, whenever it tries to give the player a
     * certain achievement, which is equivalent to a fish being called. But
     * the achievement call is more unique in the vanilla call. It creates a
     * treasure map when conditions are met.
     */
    private void AddMethodCallsFishing() {
        try {
            CtMethod method = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Fish")
                    .getMethod("fish", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFLcom/wurmonline/server/behaviours/Action;)Z");

            method.instrument(new ExprEditor() {
                private boolean done = false;

                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (!done && methodCall.getMethodName().equals("achievement")) {
                        done = true;

                        method.insertAt(
                                methodCall.getLineNumber() + 1,
                                "{ com.pveplands.treasurehunting.Treasuremap.CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(10033), (com.wurmonline.server.creatures.Creature)null); }"
                        );
                    }
                }
            });
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add methods to fishing.", e);
        }
    }

    /**
     * Injects calls to the terraforming class whenever something is dug,
     * like dirt, clay, or tar, and when flattening or leveling modifies
     * the terrain. It creates a treasuremap when conditions are met.
     */
    private void AddMethodCallsTerraforming() {
        try {
            CtMethod method = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Terraforming")
                    .getMethod("dig", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFZLcom/wurmonline/mesh/MeshIO;)Z");

            method.instrument(new ExprEditor() {
                private boolean done = false;

                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (!done && methodCall.getMethodName().equals("createItem")){
                        method.insertAt(methodCall.getLineNumber() + 1, "{ com.pveplands.treasurehunting.Treasuremap.CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(1009), (com.wurmonline.server.creatures.Creature)null); }");
                        done = true;
                    }
                }
            });

            // private static final boolean flatten(long borderId, Creature performer, Item source, int tile, int tilex, int tiley, int endX, int endY, int numbCorners, float counter, Action act) {
            CtMethod flatten = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Flattening")
                    .getMethod("flatten", "(JLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIIIIFLcom/wurmonline/server/behaviours/Action;)Z");

            flatten.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("resetChangedTiles")) {
                        flatten.insertAt(methodCall.getLineNumber() + 1, "{ com.pveplands.treasurehunting.Treasuremap.CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(1009), (com.wurmonline.server.creatures.Creature)null); }");
                        logger.log(Level.INFO, "Found method call in Flattening, inserting call to treasuremap generation.");
                    }
                }
            });
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to terraforming.", e);
        }
    }

    public void AddMethodCallsWoodcutting(){
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Add woodcutting hook.");
            CtClass ctTerraforming = classPool.get("com.wurmonline.server.behaviours.Terraforming");
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, source, performer.getSkills().getSkillOrLearn(1007), null);";
            Util.instrumentDeclared(thisClass, ctTerraforming, "handleChopAction", "setAuxData", replace);

        }catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to terraforming.", e);
        }
    }

    public void AddMethodCallsForaging(){
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Add foraging hook.");
            CtClass ctTileBehaviour = classPool.get("com.wurmonline.server.behaviours.TileBehaviour");
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, null, performer.getSkills().getSkillOrLearn(10071), null);";
            Util.instrumentDeclared(thisClass, ctTileBehaviour, "forageV11", "createItem", replace);

        }catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to terraforming.", e);
        }
    }

    public void AddMethodCallsInvestigate(){
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Add Investigate hook.");
            CtClass ctTileBehaviour = classPool.get("com.wurmonline.server.behaviours.TileBehaviour");
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, null, performer.getSkills().getSkillOrLearn(10069), null);";
            Util.instrumentDeclared(thisClass, ctTileBehaviour, "investigateTile", "createItem", replace);

        }catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to terraforming.", e);
        }
    }

    public void AddMethodCallsPrayer(){
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();
            Class<TreasureHunting> thisClass = TreasureHunting.class;
            String replace;

            Util.setReason("Add Prayer hook.");
            CtClass ctReligionBehaviour = classPool.get("com.wurmonline.server.behaviours.MethodsReligion");
            replace = "$_ = $proceed($$);" +
                    Treasuremap.class.getName()+".CreateTreasuremap(performer, null, performer.getSkills().getSkillOrLearn(10066), null);";
            Util.instrumentDeclared(thisClass, ctReligionBehaviour, "prayResult", "createItem", replace);

        }catch (Exception e) {
            logger.log(Level.SEVERE, "Can't add method calls to prayer.", e);
        }
    }

    @Override
    public void init() {
    }
}
