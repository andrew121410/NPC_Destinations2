package net.livecar.nuttyworks.npc_destinations;

import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.event.CitizensDisableEvent;
import net.livecar.nuttyworks.npc_destinations.bridges.*;
import net.livecar.nuttyworks.npc_destinations.citizens.Citizens_Processing;
import net.livecar.nuttyworks.npc_destinations.citizens.Citizens_Utilities;
import net.livecar.nuttyworks.npc_destinations.citizens.Citizens_WaypointProvider;
import net.livecar.nuttyworks.npc_destinations.citizens.NPCDestinationsTrait;
import net.livecar.nuttyworks.npc_destinations.lightapi.LightAPI_Plugin;
import net.livecar.nuttyworks.npc_destinations.listeners.BlockStickListener_NPCDest;
import net.livecar.nuttyworks.npc_destinations.listeners.PlayerJoinListener_NPCDest;
import net.livecar.nuttyworks.npc_destinations.listeners.commands.Command_Manager;
import net.livecar.nuttyworks.npc_destinations.listeners.commands.Commands_Location;
import net.livecar.nuttyworks.npc_destinations.listeners.commands.Commands_NPC;
import net.livecar.nuttyworks.npc_destinations.listeners.commands.Commands_Plugin;
import net.livecar.nuttyworks.npc_destinations.messages.Language_Manager;
import net.livecar.nuttyworks.npc_destinations.messages.Messages_Manager;
import net.livecar.nuttyworks.npc_destinations.messages.jsonChat;
import net.livecar.nuttyworks.npc_destinations.metrics.BStat_Metrics;
import net.livecar.nuttyworks.npc_destinations.particles.*;
import net.livecar.nuttyworks.npc_destinations.pathing.AstarPathFinder;
import net.livecar.nuttyworks.npc_destinations.plugins.Plugin_Manager;
import net.livecar.nuttyworks.npc_destinations.plugins.timemanager.DestinationsTimeManager;
import net.livecar.nuttyworks.npc_destinations.plugins.timemanager.realworldtime.DestinationsRealWorldTimeManager;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.betonquest.BetonQuest_Interface;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.betonquest.BetonQuest_Plugin;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.jobsreborn.JobsReborn_Plugin;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.plotsquared.PlotSquared;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.plotsquared.PlotSquared_Plugin_V3;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.plotsquared.PlotSquared_Plugin_V6;
import net.livecar.nuttyworks.npc_destinations.thirdpartyplugins.sentinel.Sentinel_Plugin;
import net.livecar.nuttyworks.npc_destinations.utilities.Utilities;
import net.livecar.nuttyworks.npc_destinations.worldguard.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

//  use regions to define he wonder area
public class DestinationsPlugin extends org.bukkit.plugin.java.JavaPlugin implements org.bukkit.event.Listener {

    public static DestinationsPlugin Instance            = null;

    // For quick reference to this instance of the plugin.
    public FileConfiguration         getDefaultConfig;

    // variables
    public List<DebugTarget>         debugTargets        = null;
    public jsonChat                  jsonChat            = null;
    public AstarPathFinder           getPathClass        = null;
    public String                    currentLanguage     = "en_def";
    public Level                     debugLogLevel       = Level.OFF;
    public int                       maxDistance         = 500;
    public int                       Version             = 10000;
    public int                       entityRadius        = 47 * 47;

    // Storage locations
    public File                      languagePath;
    public File                      loggingPath;

    // Links to classes
    public Language_Manager          getLanguageManager  = null;
    public Messages_Manager          getMessageManager   = null;
    public Citizens                  getCitizensPlugin   = null;
    public BetonQuest_Interface      getBetonQuestPlugin = null;
    public LightAPI_Plugin           getLightPlugin      = null;
    public JobsReborn_Plugin         getJobsRebornPlugin = null;
    public Sentinel_Plugin           getSentinelPlugin   = null;
    public Plugin_Manager            getPluginManager    = null;
    public WorldGuardInterface       getWorldGuardPlugin = null;
    public PlayParticleInterface getParticleManager  = null;
    public Utilities                 getUtilitiesClass   = null;
    public Command_Manager           getCommandManager   = null;
    public Citizens_Processing       getCitizensProc     = null;
    public PlotSquared               getPlotSquared      = null;
    public MCUtilsBridge             getMCUtils          = null;
    public DestinationsTimeManager   getTimeManager      = null;

    public void onLoad() {
        DestinationsPlugin.Instance = this;
        getUtilitiesClass = new Utilities(this);

        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getServer().getLogger().log(Level.WARNING, "Worldguard not found, custom flags are not enabled");
        } else {
            String wgVer = getServer().getPluginManager().getPlugin("WorldGuard").getDescription().getVersion();
            if (wgVer.contains(";"))
                wgVer = wgVer.substring(0, wgVer.indexOf(";"));
            if (wgVer.contains("-SNAPSHOT"))
                wgVer = wgVer.substring(0, wgVer.indexOf("-"));
            if (wgVer.startsWith("v"))
                wgVer = wgVer.substring(1);

            String[] parts = wgVer.split("[.]");

            int majorVersion = 0;

            boolean goodVersion = false;
            try {
                Integer[] verPart = new Integer[3];
                if (getUtilitiesClass.isNumeric(parts[0])) {
                    verPart[0] = Integer.parseInt(parts[0]);
                }

                if (getUtilitiesClass.isNumeric(parts[1])) {
                    verPart[1] = Integer.parseInt(parts[1]);
                }

                if (parts.length > 2 && getUtilitiesClass.isNumeric(parts[2])) {
                    verPart[2] = Integer.parseInt(parts[2]);
                }

                if (verPart[0] == 6 && verPart[1] == 1 && verPart[2] >= 3) {
                    majorVersion = 6;
                    goodVersion = true;
                } else if (verPart[0] == 6 && verPart[1] > 1) {
                    majorVersion = 6;
                    goodVersion = true;
                } else if (verPart[0] == 6) {
                    goodVersion = true;
                    majorVersion = 6;
                } else if (verPart[0] >= 7) {
                    goodVersion = true;
                    majorVersion = 7;
                }

            } catch (Exception err) {
                goodVersion = false;
            }

            if (!goodVersion) {
                getServer().getLogger().log(Level.WARNING, "This Worldguard version is not supported, custom flags are not enabled");
            } else {
                if (majorVersion == 6 && WorldGuard_6_2_2.isValidVersion())
                    this.getWorldGuardPlugin = new WorldGuard_6_2_2(this);
                else if (majorVersion == 7 && WorldGuard_7_0_1.isValidVersion())
                    this.getWorldGuardPlugin = new WorldGuard_7_0_1(this);
                else if (majorVersion == 7 && WorldGuard_7_0_3.isValidVersion())
                    this.getWorldGuardPlugin = new WorldGuard_7_0_3(this);
                else if (majorVersion == 7 && WorldGuard_7_0_0.isValidVersion())
                    this.getWorldGuardPlugin = new WorldGuard_7_0_0(this);
                this.getWorldGuardPlugin.registerFlags();
            }
        }
    
    
        if (getServer().getPluginManager().getPlugin("Quests") != null) {
            //Write out the quests addon to the quests modules folder.
            if (new File(this.getDataFolder().getParentFile(), "/Quests/modules").exists())
                exportFile(new File(this.getDataFolder().getParentFile(), "/Quests/modules"), "NPCDestinations_Quests-2.3.0.jar",true);
        }

    }

    public void onEnable() {

        // Setup defaults
        debugTargets = new ArrayList<DebugTarget>();
        getLanguageManager = new Language_Manager(this);
        getMessageManager = new Messages_Manager(this);
        getPluginManager = new Plugin_Manager(this);
        getCommandManager = new Command_Manager(this);
        getCitizensProc = new Citizens_Processing(this);

        // Setup the default paths in the storage folder.
        languagePath = new File(this.getDataFolder(), "/Languages/");
        loggingPath = new File(this.getDataFolder(), "/Logs/");

        // Generate the default folders and files.
        getDefaultConfigs();

        // Get languages
        getLanguageManager.loadLanguages();

        // Init Default settings
        if (this.getDefaultConfig.contains("language"))
            this.currentLanguage = this.getDefaultConfig.getString("language");
        if (this.currentLanguage.equalsIgnoreCase("en-default"))
            this.currentLanguage = "en_def";

        if (this.getDefaultConfig.contains("max-distance"))
            this.maxDistance = this.getDefaultConfig.getInt("max-distance", 500);
        if (this.getDefaultConfig.contains("max-distance"))
            this.maxDistance = this.getDefaultConfig.getInt("max-distance", 500);

        // Register commands
        getCommandManager.registerCommandClass(Commands_Plugin.class);
        getCommandManager.registerCommandClass(Commands_NPC.class);
        getCommandManager.registerCommandClass(Commands_Location.class);

        // Mark the version
        if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_8_R3")) {
            Version = 10808;
            getParticleManager = new PlayParticle_1_8_R3();
            getMCUtils = new MCUtil_1_8_R3();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_9_R2")) {
            Version = 10902;
            getParticleManager = new PlayParticle_1_9_R2();
            getMCUtils = new MCUtil_1_9_R2();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_10_R1")) {
            Version = 11000;
            getParticleManager = new PlayParticle_1_10_R1();
            getMCUtils = new MCUtil_1_10_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_11_R1") && getServer().getVersion().endsWith("MC: 1.11)")) {
            Version = 11100;
            getParticleManager = new PlayParticle_1_11_R1();
            getMCUtils = new MCUtil_1_11_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_11_R1") && getServer().getVersion().endsWith("MC: 1.11.1)")) {
            Version = 11110;
            getParticleManager = new PlayParticle_1_11_R1();
            getMCUtils = new MCUtil_1_11_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_11_R1") && getServer().getVersion().endsWith("MC: 1.11.2)")) {
            Version = 11120;
            getParticleManager = new PlayParticle_1_11_R1();
            getMCUtils = new MCUtil_1_11_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_12_R1")) {
            Version = 11200;
            getParticleManager = new PlayParticle_1_12_R1();
            getMCUtils = new MCUtil_1_12_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_13_R2")) {
            Version = 11310;
            getParticleManager = new PlayParticle_1_13_R2();
            getMCUtils = new MCUtil_1_13_R2();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_14_R1")) {
            Version = 11410;
            getParticleManager = new PlayParticle_1_14_R1();
            getMCUtils = new MCUtil_1_14_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_14_R2")) {
            Version = 11420;
            getParticleManager = new PlayParticle_1_14_R2();
            getMCUtils = new MCUtil_1_14_R2();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_15_R1")) {
            Version = 11520;
            getParticleManager = new PlayParticle_1_15_R1();
            getMCUtils = new MCUtil_1_15_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_16_R1")) {
            Version = 11610;
            getParticleManager = new PlayParticle_1_16_R1();
            getMCUtils = new MCUtil_1_16_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_16_R2")) {
            Version = 11620;
            getParticleManager = new PlayParticle_1_16_R2();
            getMCUtils = new MCUtil_1_16_R2();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_16_R3")) {
            Version = 11640;
            getParticleManager = new PlayParticle_1_16_R3();
            getMCUtils = new MCUtil_1_16_R3();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else if (Bukkit.getServer().getClass().getPackage().getName().endsWith("v1_17_R1")) {
            Version = 11710;
            getParticleManager = new PlayParticle_1_17_R1();
            getMCUtils = new MCUtil_1_17_R1();
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_version", getServer().getVersion().substring(getServer().getVersion().indexOf('(')));
        } else {
            getMessageManager.consoleMessage(this, "destinations", "console_messages.plugin_unknownversion", Bukkit.getServer().getClass().getPackage().getName());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        //Determine the time engine
        String timePlugin = this.getConfig().getString("timeplugin","default");
        
        switch (timePlugin.toUpperCase())
        {
            case "REALWORLD":
                this.getTimeManager = new DestinationsRealWorldTimeManager();
                break;
            default:
                this.getTimeManager = new DestinationsTimeManager();
        }
        
        getPathClass = new AstarPathFinder(this);
    
        // Init links to other plugins
        if (getServer().getPluginManager().getPlugin("Citizens") == null || getServer().getPluginManager().getPlugin("Citizens").isEnabled() == false || !(getServer().getPluginManager().getPlugin("Citizens") instanceof Citizens)) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|CitizensNotFound");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.citizens_notfound");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            getCitizensPlugin = (Citizens) getServer().getPluginManager().getPlugin("Citizens");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.citizens_found", getCitizensPlugin.getDescription().getVersion());
        }

        if (getServer().getPluginManager().getPlugin("BetonQuest") == null) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|BetonQuest_NotFound");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.betonquest_notfound");
        } else {
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.betonquest_found", getServer().getPluginManager().getPlugin("BetonQuest").getDescription().getVersion());
            getBetonQuestPlugin = new BetonQuest_Plugin(this);
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|BetonQuestFound");
        }

        if (getServer().getPluginManager().getPlugin("LightAPI") == null) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|LightAPI_NotFound");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.lightapi_notfound");
        } else {
            getLightPlugin = new LightAPI_Plugin(this);
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|LightAPI_Found");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.lightapi_found", getServer().getPluginManager().getPlugin("LightAPI").getDescription().getVersion());
        }

        // 1.31 - Jobs Reborn
        if (getServer().getPluginManager().getPlugin("Jobs") == null) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|JobsReborn_NotFound");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.jobsreborn_notfound");
        } else {
            getJobsRebornPlugin = new JobsReborn_Plugin(this);
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|JobsReborn_Found");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.jobsreborn_found");
        }

        // 1.39 - Sentinel!
        if (getServer().getPluginManager().getPlugin("Sentinel") == null) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|Sentinel_NotFound");
            getMessageManager.consoleMessage(this, "sentinel", "Console_Messages.sentinel_notfound");
        } else {
            this.getSentinelPlugin = new Sentinel_Plugin(this);
            getMessageManager.consoleMessage(this, "sentinel", "Console_Messages.sentinel_found", getSentinelPlugin.getVersionString());
        }

        // 2.1.8 - Plotsquared compliance
        if (getServer().getPluginManager().getPlugin("PlotSquared") == null) {
            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|plotsquared_NotFound");
            getMessageManager.consoleMessage(this, "destinations", "Console_Messages.plotsquared_notfound");
        } else {

            if (getPlotSquared == null) {
                try {
                    Class.forName("com.github.intellectualsites.plotsquared.plot.flag.Flag");
                    this.getPlotSquared = new PlotSquared_Plugin_V6();
                    getMessageManager.consoleMessage(this, "destinations", "Console_Messages.plotsquared_found","V4-" + getServer().getPluginManager().getPlugin("PlotSquared").getDescription().getVersion());
                } catch (Exception e) {
                }
            }

            if (getPlotSquared == null) {
                try {
                    Class.forName("com.intellectualcrafters.plot.flag.Flag");
                    getMessageManager.consoleMessage(this, "destinations", "Console_Messages.plotsquared_found","V3-" + getServer().getPluginManager().getPlugin("PlotSquared").getDescription().getVersion());
                    this.getPlotSquared = new PlotSquared_Plugin_V3();

                } catch (Exception e) {
                }
            }

            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onEnable()|plotsquared_Found");
        }

        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getMessageManager.consoleMessage(this, "destinations", "console_messages.worldguard_notfound");
        } else {
            String wgVer = getServer().getPluginManager().getPlugin("WorldGuard").getDescription().getVersion();
            if (wgVer.contains(";"))
                wgVer = wgVer.substring(0, wgVer.indexOf(";"));
            if (wgVer.contains("-SNAPSHOT"))
                wgVer = wgVer.substring(0, wgVer.indexOf("-"));
            if (wgVer.startsWith("v"))
                wgVer = wgVer.substring(1);

            String[] parts = wgVer.split("[.]");

            boolean goodVersion = false;
            Integer[] verPart = new Integer[3];
            if (getUtilitiesClass.isNumeric(parts[0])) {
                verPart[0] = Integer.parseInt(parts[0]);
            }

            if (getUtilitiesClass.isNumeric(parts[1])) {
                verPart[1] = Integer.parseInt(parts[1]);
            }

            if (parts.length < 3) {
                goodVersion = false;
            } else {

                if (parts.length > 2 && getUtilitiesClass.isNumeric(parts[2])) {
                    verPart[2] = Integer.parseInt(parts[2]);
                }

                if (verPart[0] == 6 && verPart[1] == 1 && verPart[2] >= 3) {
                    goodVersion = true;
                } else if (verPart[0] == 6 && verPart[1] > 1) {
                    goodVersion = true;
                } else if (verPart[0] > 6) {
                    goodVersion = true;
                }
            }

            if (!goodVersion) {
                getMessageManager.consoleMessage(this, "destinations", "console_messages.worldguard_unsupported", getServer().getPluginManager().getPlugin("WorldGuard").getDescription().getVersion());
            } else {
                getMessageManager.consoleMessage(this, "destinations", "console_messages.worldguard_found", getServer().getPluginManager().getPlugin("WorldGuard").getDescription().getVersion());
                this.getWorldGuardPlugin.registerEvents();
            }
        }

        jsonChat = new net.livecar.nuttyworks.npc_destinations.messages.jsonChat(this);

        // Register your trait with Citizens.
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(NPCDestinationsTrait.class).withName("npcdestinations"));

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getPluginManager().registerEvents(new BlockStickListener_NPCDest(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener_NPCDest(this), this);

        net.citizensnpcs.trait.waypoint.Waypoints.registerWaypointProvider(Citizens_WaypointProvider.class, "npcdestinations");

        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                try {
                    getPathClass.CheckStatus();
                } catch (Exception e) {
                }
            }
        }, 30L, 5L);

        // 1.34 - Citizens save.yml backup monitor
        final Citizens_Utilities backupClass = new Citizens_Utilities(this);

        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                backupClass.BackupConfig(false);
            } catch (Exception e) {
            }
        }, 1200L, 1200L);

        final BStat_Metrics statsReporting = new BStat_Metrics(this);

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, statsReporting::Start,500L);
    }

    public void onDisable() {
        if (isEnabled()) {

            this.getMessageManager.debugMessage(Level.CONFIG, "nuNPCDestinations.onDisable()|Stopping Internal Processes");
            Bukkit.getServer().getScheduler().cancelTasks(this);
            getPathClass.currentTask = null;
            getPathClass.path_Queue.clear();
            getPathClass = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] inargs) {
        if (cmd.getName().equalsIgnoreCase("npcdest") | cmd.getName().equalsIgnoreCase("nd")) {
            return getCommandManager.onCommand(sender, inargs);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String cmdLabel, String[] inargs) {
        if (cmd.getName().equalsIgnoreCase("npcdest") | cmd.getName().equalsIgnoreCase("nd")) {
            return getCommandManager.onTabComplete(sender, inargs);
        }
        return new ArrayList<String>();
    }

    @EventHandler
    public void CitizensDisabled(final CitizensDisableEvent event) {
        Bukkit.getServer().getScheduler().cancelTasks(this);
        getPathClass = null;
        getMessageManager.consoleMessage(this, "destinations", "Console_Messages.plugin_ondisable");
        getServer().getPluginManager().disablePlugin(this);
    }

    public Boolean hasPermissions(CommandSender player, String[] permissions) {
        for (String perm : permissions) {
            if (hasPermissions(player, perm))
                return true;
        }
        return false;
    }

    public Boolean hasPermissions(CommandSender player, String permission) {
        if (player instanceof Player) {
            if (player.isOp())
                return true;

            if (permission.toLowerCase().startsWith("npcdestinations.editall.") && player.hasPermission("npcdestinations.editall.*"))
                return true;

            if (permission.toLowerCase().startsWith("npcdestinations.editown.") && player.hasPermission("npcdestinations.editown.*"))
                return true;

            return player.hasPermission(permission);
        }
        return true;
    }

    private void getDefaultConfigs() {
        // Create the default folders
        if (!this.getDataFolder().exists())
            this.getDataFolder().mkdirs();
        if (!languagePath.exists())
            languagePath.mkdirs();
        if (!loggingPath.exists())
            loggingPath.mkdirs();

        // Validate that the default package is in the MountPackages folder. If
        // not, create it.
        if (!(new File(getDataFolder(), "config.yml").exists()))
            exportFile(getDataFolder(), "config.yml", false);
        exportFile(languagePath, "en_def-destinations.yml",true);
        exportFile(languagePath, "en_def-jobsreborn.yml",true);
        exportFile(languagePath, "en_def-sentinel.yml",true);

        this.getDefaultConfig = getUtilitiesClass.loadConfiguration(new File(this.getDataFolder(), "config.yml"));
    }

    private void exportFile(File path, String filename, boolean overwrite) {
        if (getMessageManager != null)
            this.getMessageManager.debugMessage(Level.FINEST, "nuDestinationsPlugin.exportFile()|");
        File fileConfig = new File(path, filename);
        if (!fileConfig.isDirectory()) {
            try {
                exportFile(filename, fileConfig, overwrite);
            } catch (IOException e1) {
                if (getMessageManager != null) {
                    getMessageManager.debugMessage(Level.SEVERE, "nuDestinationsPlugin.exportFile()|FailedToExtractFile(" + filename + ")");
                    getMessageManager.logToConsole(this, " Failed to extract default file (" + filename + ")");
                }
            }
        }
    }

    private void exportFile(String source, File destination,boolean overwrite) throws IOException {
        //We overwrite the files anyway
        if (!overwrite && destination.exists())
            return;

        if (destination.exists())
            destination.delete();

        if (!destination.getParentFile().exists())
            throw new IOException("Folders missing.");

        if (!destination.createNewFile())
            throw new IOException("Failed to create a new file");

        URL sourceURL = getClass().getResource("/" + source);
        if (sourceURL == null)
            throw new IOException("Missing resource file");

        byte[] ioBuffer = new byte[1024];
        int bytesRead = 0;

        try {
            URLConnection inputConnection = sourceURL.openConnection();
            inputConnection.setUseCaches(false);

            InputStream fileIn = inputConnection.getInputStream();
            OutputStream fileOut = new FileOutputStream(destination);

            while ((bytesRead = fileIn.read(ioBuffer)) > 0) {
                fileOut.write(ioBuffer,0,bytesRead);
            }

            fileOut.flush();
            fileOut.close();
            fileIn.close();
        } catch (Exception error)
        {
            throw new IOException("Failure exporting file");
        }
    }
}
