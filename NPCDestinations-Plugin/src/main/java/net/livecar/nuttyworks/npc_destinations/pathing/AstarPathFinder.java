package net.livecar.nuttyworks.npc_destinations.pathing;

import net.citizensnpcs.api.npc.NPC;
import net.livecar.nuttyworks.npc_destinations.DebugTarget;
import net.livecar.nuttyworks.npc_destinations.DestinationsPlugin;
import net.livecar.nuttyworks.npc_destinations.citizens.NPCDestinationsTrait;
import net.livecar.nuttyworks.npc_destinations.citizens.NPCDestinationsTrait.en_CurrentAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.material.Openable;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;

public class AstarPathFinder {

    private boolean                                 bInWater    = false;
    private List<Player>                            playToPlayers;
    private DestinationsPlugin                      destRef     = null;
    private Long                                    last_Pause  = 0L;

    // 1.6
    public PathFindingQueue                         currentTask = null;
    public LinkedHashMap<Integer, PathFindingQueue> path_Queue  = new LinkedHashMap<>();

    public AstarPathFinder(DestinationsPlugin storageRef) {
        destRef = storageRef;
    }

    public void CheckStatus() {
        if (destRef.debugLogLevel == Level.FINEST)
            destRef.getMessageManager.debugMessage(Level.FINEST, Arrays.toString(Thread.currentThread().getStackTrace()));

        if ((currentTask == null || currentTask.npc == null) && path_Queue.size() > 0) {
            // Fire off the initial task
            NextQueue();
        }
    }

    public boolean isLocationWalkable(Location l, boolean opensGates, boolean opensWoodDoors, boolean opensMetalDoors) {
        if (destRef.debugLogLevel == Level.FINEST)
            destRef.getMessageManager.debugMessage(Level.FINEST, "(Location " + l.toString() + ",boolean " + opensGates + ", boolean " + opensWoodDoors
                    + ", boolean " + opensMetalDoors + ") " + Arrays.toString(Thread.currentThread().getStackTrace()));
        return destRef.getMCUtils.isLocationWalkable(l, opensGates, opensWoodDoors, opensMetalDoors);
    }

    public boolean isLocationWalkable(Location l) {
        if (currentTask == null)
            return false;

        destRef.getMessageManager.debugMessage(Level.FINEST, "(Location " + l.toString() + ") " + Arrays.toString(Thread.currentThread()
                .getStackTrace()));
        return destRef.getMCUtils.isLocationWalkable(l, currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors);
    }

    public boolean requiresOpening(Location l) {
        destRef.getMessageManager.debugMessage(Level.FINEST, "(Location " + l.toString() + ")" + Arrays.toString(Thread.currentThread().getStackTrace()));

        return destRef.getMCUtils.requiresOpening(l);
    }

    public void addToQueue(NPC npc, NPCDestinationsTrait npcTrait, Location start, Location end, int range, List<Material> AllowedPathBlocks, int blocksBelow, Boolean OpensGates, Boolean OpensWoodDoors, Boolean OpensMetalDoors,
                           String requestedBy) {
        destRef.getMessageManager.debugMessage(Level.FINEST, "(NPC " + npc.getId() + ", NPCDestinationsTrait " + String.valueOf(npcTrait == null) + ", Location "
                + start.toString() + ", Location " + end.toString() + ", int " + String.valueOf(range) + ", List<Material> " + AllowedPathBlocks.size() + ", int " + String.valueOf(blocksBelow) + ", Boolean " + String.valueOf(OpensGates)
                + ", Boolean " + String.valueOf(OpensWoodDoors) + ", Boolean " + String.valueOf(OpensMetalDoors) + ",String " + String.valueOf(requestedBy) + ") " + Arrays.toString(Thread.currentThread().getStackTrace()));

        if (playToPlayers != null)
            playToPlayers.clear();

        //Short pathing faults
        if (npc.getEntity().getLocation().distanceSquared(end) < 4)
        {
            //Just teleport the npc
            if (destRef.getMCUtils.isHalfBlock(npc.getEntity().getLocation().getBlock().getType()))
            {
                npc.getEntity().teleport(new Location(
                        npc.getEntity().getLocation().getWorld()
                        , npc.getEntity().getLocation().getBlockX()
                        , npc.getEntity().getLocation().getBlockY()+1
                        , npc.getEntity().getLocation().getBlockZ()));
            } else {
                npc.getEntity().teleport(new Location(
                        npc.getEntity().getLocation().getWorld()
                        , npc.getEntity().getLocation().getBlockX()
                        , npc.getEntity().getLocation().getBlockY()
                        , npc.getEntity().getLocation().getBlockZ()));
            }
            return;
        }
        
        
        if (destRef.debugTargets != null && destRef.debugTargets.size() > 0) {

            playToPlayers = new ArrayList<Player>();
            for (DebugTarget debugOutput : destRef.debugTargets) {
                if ((debugOutput.targetSender instanceof Player) && (debugOutput.getTargets().size() == 0 || debugOutput.getTargets().contains(npc.getId())))
                    playToPlayers.add((Player) debugOutput.targetSender);
            }
        }

        // 1.6 Queue System
        if (path_Queue == null)
            path_Queue = new LinkedHashMap<Integer, PathFindingQueue>();

        if (path_Queue.containsKey(npc.getId())) {
            if (currentTask != null && currentTask.npc != null) {
                return;
            } else {
                if (last_Pause < new Date().getTime())
                    NextQueue();
                return;
            }
        }

        // Fix the start location
        Location cleanStart = start.clone();
        Location cleanEnd = end.clone();

        if (!cleanStart.clone().getBlock().getType().isSolid())
            cleanStart = findSurface(cleanStart.clone());

        if (!cleanEnd.clone().getBlock().getType().isSolid())
            cleanEnd = findSurface(cleanEnd.clone());

        // Add to the queue
        PathFindingQueue oQueueItem = new PathFindingQueue();
        oQueueItem.world = cleanStart.getWorld();
        oQueueItem.start_X = cleanStart.getBlockX();
        oQueueItem.start_Y = cleanStart.getBlockY();
        oQueueItem.start_Z = cleanStart.getBlockZ();
        oQueueItem.end_X = cleanEnd.getBlockX();
        oQueueItem.end_Y = cleanEnd.getBlockY();
        oQueueItem.end_Z = cleanEnd.getBlockZ();
        oQueueItem.range = range;
        oQueueItem.npcTrait = npcTrait;
        oQueueItem.npc = npc;
        oQueueItem.opensGates = OpensGates;
        oQueueItem.opensMetalDoors = OpensMetalDoors;
        oQueueItem.opensWoodDoors = OpensWoodDoors;
        oQueueItem.allowedPathBlocks = new ArrayList<Material>();
        oQueueItem.allowedPathBlocks.addAll(AllowedPathBlocks);
        oQueueItem.setBlocksBelow(blocksBelow);
        oQueueItem.requestedBy = requestedBy;
        oQueueItem.timeSpent = 0L;
        oQueueItem.blocksProcessed = 0L;
        oQueueItem.open = new HashMap<String, Tile>();
        oQueueItem.closed = new HashMap<String, Tile>();

        // 1/2 slab checks - V1.19
        // Check if the start location is a 1/2 slab
        if (destRef.getMCUtils.isHalfBlock(cleanStart.getBlock().getRelative(0, 1, 0).getType())) {
            if (!cleanStart.getBlock().getRelative(0, 2, 0).getType().isSolid() && !cleanStart.getBlock().getRelative(0, 3, 0).getType().isSolid()) {
                oQueueItem.start_Y++;
            }
        }

        // Check if the end location is a 1/2 slab
        if (destRef.getMCUtils.isHalfBlock(cleanEnd.getBlock().getRelative(0, 1, 0).getType())) {
            if (!cleanEnd.getBlock().getRelative(0, 2, 0).getType().isSolid() && !cleanEnd.getBlock().getRelative(0, 3, 0).getType().isSolid()) {
                oQueueItem.end_Y++;
            }
        }

        if (currentTask != null && currentTask.npc != null) {
            if (currentTask.processingStarted != null) {
                long nSeconds = (new Date().getTime() - currentTask.processingStarted.getTime()) / 1000L % 60L;
                if (nSeconds > 5) {
                    destRef.getMessageManager.debugMessage(Level.FINEST, "CurrentTask to long, " + currentTask.npc.getId());
                    CleanTask();
                    currentTask = null;
                    return;
                }
            }

            if (currentTask.npc.getId() == npc.getId()) {
                return;
            }
            path_Queue.put(npc.getId(), oQueueItem);
            destRef.getMessageManager.debugMessage(Level.FINEST, "QUEUED NPC: " + oQueueItem.npc.getId() + "|" + oQueueItem.end_X + "," + oQueueItem.end_Y + "," + oQueueItem.end_Z);
        } else {
            currentTask = oQueueItem;
            currentTask.processingStarted = new Date();
            destRef.getMessageManager.debugMessage(Level.FINEST, "CurrentTask Idle, Processing " + currentTask.npc.getId());
            ProcessQueueItem();
        }
    }

    private void addToOpenList(Tile t, boolean modify) {
        if (currentTask.open.containsKey(t.getUID())) {
            if (modify) {
                currentTask.open.put(t.getUID(), t);
            }
        } else {
            currentTask.open.put(t.getUID(), t);
        }

        if (playToPlayers != null && playToPlayers.size() > 0) {
            for (Player player : playToPlayers)
                destRef.getParticleManager.PlayOutHeartParticle(t.getLocation(new Location(currentTask.world, currentTask.start_X, currentTask.start_Y, currentTask.start_Z).add(0.5,0,0.5)), player);
        }
    }

    private void addToClosedList(Tile t) {
        if (!currentTask.closed.containsKey(t.getUID())) {
            currentTask.closed.put(t.getUID(), t);
        }
    }

    private void ProcessQueueItem() {
        if (currentTask == null)
            return;
        if (currentTask.npcTrait == null) {
            CleanTask();
            return;
        }

        if (destRef.debugLogLevel == Level.FINEST)
            destRef.getMessageManager.debugMessage(Level.FINEST, "NPC: " + currentTask.npc.getId() + "|" + Arrays.toString(Thread.currentThread().getStackTrace()));

        bInWater = false;

        // Start looking for a path on this NPC
        if (getStartLocation().getBlock().isLiquid() && destRef.getMCUtils.isLocationWalkable(getEndLocation(), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
            bInWater = true;
        } else {
            if (!destRef.getMCUtils.isLocationWalkable(getStartLocation(), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
                for (byte y = -1; y <= 1; y++) {
                    for (byte x = -1; x <= 1; x++) {
                        for (byte z = -1; z <= 1; z++) {

                            if (destRef.getMCUtils.isLocationWalkable(getStartLocation().add(x, y, z), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
                                // start here?
                                currentTask.start_X = currentTask.start_X + x;
                                currentTask.start_Y = currentTask.start_Y + y;
                                currentTask.start_Z = currentTask.start_Z + z;
                            }
                        }
                    }
                }
            }

            if (!destRef.getMCUtils.isLocationWalkable(getEndLocation(), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
                if (!getEndLocation().getBlock().getType().isSolid())
                    currentTask.end_Y--;
            }

            if ((abs(currentTask.start_X - currentTask.end_X) > currentTask.range) || (abs(currentTask.start_Y - currentTask.end_Y) > currentTask.range) || (abs(currentTask.start_Z - currentTask.end_Z) > currentTask.range)) {
                destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_outofrange", currentTask.npc, currentTask.npcTrait);
                destRef.getMessageManager.debugMessage(Level.INFO, "NPCDestinations_astar.ProcessQueueItem().FailedPath|NPC:" + currentTask.npc.getId() + "|Requested: " + currentTask.requestedBy);
                currentTask.npcTrait.lastResult = "Unable to find a path";
                currentTask.npcTrait.setCurrentAction(en_CurrentAction.IDLE_FAILED);
                currentTask.npcTrait.setLastPathCalc();
                currentTask.npcTrait.setLocationLockUntil(LocalDateTime.now().plusSeconds(10));
                CleanTask();
                return;// jump out
            }

            if (!destRef.getMCUtils.isLocationWalkable(getStartLocation(), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors) && !destRef.getMCUtils.isLocationWalkable(getEndLocation(), currentTask.opensGates,
                    currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
                destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_badendloc", currentTask.npc, currentTask.npcTrait, "S/E Fail [" + currentTask.end_X + "," + currentTask.end_Y + "," + currentTask.end_Z + "]");
                destRef.getMessageManager.debugMessage(Level.INFO, "NPCDestinations_astar.ProcessQueueItem().FailedPath|NPC:" + currentTask.npc.getId() + "|Requested: " + currentTask.requestedBy);
                currentTask.npcTrait.lastResult = "Start/End location is not walkable";
                currentTask.npcTrait.setCurrentAction(en_CurrentAction.IDLE_FAILED);
                currentTask.npcTrait.setLastPathCalc();
                currentTask.npcTrait.setLocationLockUntil(LocalDateTime.now().plusSeconds(10));

                // 1.6 Teleport the NPC as the start is wacked.
                currentTask.npc.teleport(getEndLocation().add(0, 1, 0), TeleportCause.PLUGIN);
                currentTask.npcTrait.locationReached();
                CleanTask();
                return;
            } else if (destRef.getMCUtils.isLocationWalkable(getStartLocation(), currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors) && !destRef.getMCUtils.isLocationWalkable(getEndLocation(), currentTask.opensGates,
                    currentTask.opensWoodDoors, currentTask.opensMetalDoors)) {
                // Cannot move the NPC at all.
                destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_badendloc", currentTask.npc, currentTask.npcTrait, "E Fail [" + currentTask.end_X + "," + currentTask.end_Y + "," + currentTask.end_Z + "]");
                destRef.getMessageManager.debugMessage(Level.INFO, "NPCDestinations_astar.ProcessQueueItem().FailedPath|NPC:" + currentTask.npc.getId() + "|Start/End No Walk|Requested: " + currentTask.requestedBy);

                currentTask.npcTrait.lastResult = "End location is not walkable";
                currentTask.npcTrait.setCurrentAction(en_CurrentAction.IDLE_FAILED);
                currentTask.npcTrait.setLastPathCalc();
                currentTask.npcTrait.setLocationLockUntil(LocalDateTime.now().plusSeconds(10));
                CleanTask();
                return;
            }

            // 1.6: current task null, check to see if other tasks exist.
            if (path_Queue.size() > 0 && currentTask == null) {
                destRef.getServer().getScheduler().runTask(destRef, () -> {
                    // continue sync:
                    NextQueue();
                });
                return;
            } else if (currentTask == null) {
                return;
            }

            // 1.6 Ensure they are on a walkable block
            if (currentTask.allowedPathBlocks.size() > 0 && !currentTask.allowedPathBlocks.contains(currentTask.getPathLocation(getStartLocation()).getBlock().getType())) {
                // remove the list of blocks to ensure that the NPC can walk
                // home.
                currentTask.allowedPathBlocks.clear();
            }
        }

        currentTask.npcTrait.setLastPathCalc();

        short sh = 0;
        Tile t = new Tile(sh, sh, sh, null);
        t.calculateBoth(currentTask.start_X, currentTask.start_Y, currentTask.start_Z, currentTask.end_X, currentTask.end_Y, currentTask.end_Z, true);
        currentTask.open.put(t.getUID(), t);
        processAdjacentTiles(t);
        iterate();
    }

    private Location getEndLocation() {
        Location endLoc = new Location(currentTask.world, currentTask.end_X, currentTask.end_Y, currentTask.end_Z);
        return findSurface(endLoc);
    }

    private Location getStartLocation() {
        Location startLoc = new Location(currentTask.world, currentTask.start_X, currentTask.start_Y, currentTask.start_Z);
        return findSurface(startLoc);
    }

    private Location findSurface(Location l) {
        if (l.getBlock().getType().isSolid() && !l.clone().add(0, 1, 0).getBlock().getType().isSolid())
            return l;

        if (!l.getBlock().getType().isSolid() && l.clone().add(0, -1, 0).getBlock().getType().isSolid())
            return l.clone().add(0, -1, 0);

        for (int y = 0; y <= 3; y++) {
            if (l.clone().add(0, 0-y, 0).getBlock().getType().isSolid() && !l.clone().add(0, (0-y)+1, 0).getBlock().getType().isSolid())
                return l.clone().add(0, 0 - y, 0);
        }

        return l;
    }

    private int abs(int i) {
        return (i < 0 ? -i : i);
    }

    @SuppressWarnings({ "unchecked" })
    private void iterate() {
        // while not at end
        Tile current = null;
        int nRepCount = 0;
        Long startTime = new Date().getTime();

        if (currentTask == null)
            return;
        if (currentTask.npc == null) {
            CleanTask();
            return;
        }

        if (!currentTask.npc.isSpawned()) {
            CleanTask();
            return;
        }

        if (currentTask.npcTrait == null) {
            CleanTask();
            return;
        }

        destRef.getMessageManager.debugMessage(Level.FINEST, "NPCID:" + currentTask.npc.getId());

        NPCDestinationsTrait trait = currentTask.npc.getTrait(NPCDestinationsTrait.class);

        Integer maxSeek = destRef.getConfig().getInt("seek-time", 10);
        if (currentTask.npcTrait.maxProcessingTime > 0)
            maxSeek = currentTask.npcTrait.maxProcessingTime;

        while (canContinue()) {
            if (currentTask == null)
                return;
            if (currentTask.npcTrait == null) {
                CleanTask();
                return;
            }

            if (currentTask.npcTrait.getLastPathCalc() == null) {
                destRef.getMessageManager.debugMessage(Level.FINEST, "NPCID:" + currentTask.npc.getId() + "|No last path calc time");
                CleanTask();
                return;
            }

            if (destRef.debugTargets != null && currentTask.timeSpent == 0L) {
                currentTask.timeSpent = 1L;
                currentTask.processingStarted = new Date();
                trait.processingStarted = LocalDateTime.now();
                destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_processing", currentTask.npc, currentTask.npcTrait);
            }

            // get lowest F cost square on open list
            current = getLowestFTile();

            // process tiles
            processAdjacentTiles(current);

            // How long has this been running, to long, lets exit out.
            long nSeconds = Duration.ofMillis(currentTask.timeSpent + (new Date().getTime() - startTime)).getSeconds();

            if (nSeconds > maxSeek) {
                // Kill the search, to long.
                trait.lastProcessingTime += (new Date().getTime() - startTime) / 1000 % 60;
                trait.totalProcessedBlocks += currentTask.blocksProcessed;
                trait.totalProcessingTime += (currentTask.timeSpent + (new Date().getTime() - startTime));

                currentTask.pathFindingResult = PathingResult.NO_PATH;
                trait.lastResult = "Unable to find a path";
                trait.setCurrentAction(en_CurrentAction.IDLE_FAILED);
                trait.setLastPathCalc();
                trait.setLocationLockUntil(LocalDateTime.now().plusSeconds(10));

                destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_timeout", currentTask.npc, trait);
                destRef.getMessageManager.debugMessage(Level.INFO, "NPCDestinations_astar.ProcessQueueItem().FailedPath|NPC:" + currentTask.npc.getId() + "|Timeout|Requested: " + currentTask.requestedBy);

                CleanTask();
                return;
            }

            nRepCount++;
            if (nRepCount > 50) {

                currentTask.timeSpent += (new Date().getTime() - startTime);
                path_Queue.put(currentTask.npc.getId(), currentTask);
                Double distance = (new Location(currentTask.world,currentTask.start_X,currentTask.start_Y, currentTask.start_Z)).distance(new Location(currentTask.world,current.getX(),current.getY(),current.getZ()));
                destRef.getMessageManager.debugMessage(Level.FINEST, "NPCID:" + currentTask.npc.getId() + "|Block limit reached, re-adding task to queue (Distance: " + String.valueOf(distance) + ")");
                currentTask = null;
                last_Pause = new Date().getTime() + 200;

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        NextQueue();
                    }
                }.runTaskLater(destRef, 2);
                return;
            }
        }

        if (currentTask == null)
            return;

        if (currentTask.pathFindingResult != PathingResult.SUCCESS || current == null) {
            trait.lastProcessingTime += (new Date().getTime() - startTime) / 1000 % 60;

            destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_novalidpath", currentTask.npc, currentTask.npcTrait);
            destRef.getMessageManager.debugMessage(Level.FINEST, "NPCDestinations_astar.ProcessQueueItem().FailedPath|NPC:" + currentTask.npc.getId() + "|Timeout/Len|Requested: " + currentTask.requestedBy);

            trait.lastResult = "Unable to find a path";
            trait.setCurrentAction(en_CurrentAction.IDLE_FAILED);
            trait.setLocationLockUntil(LocalDateTime.now().plusSeconds(10));
            trait.setLastPathCalc();
            CleanTask();
            return;
        } else {
            // path found
            LinkedList<Tile> routeTrace = new LinkedList<Tile>();
            Tile parent;

            routeTrace.add(current);

            while ((parent = current.getParent()) != null) {
                routeTrace.add(parent);
                current = parent;
            }

            Collections.reverse(routeTrace);

            ArrayList<Location> locationArray = new ArrayList<Location>();
            for (Tile tLoc : routeTrace) {
                locationArray.add(tLoc.getLocation(getStartLocation()));
            }
            
            trait.setPendingDestinations(locationArray);
            long nSeconds = (currentTask.timeSpent + (new Date().getTime() - startTime)) / 1000 % 60;
            trait.lastProcessingTime = nSeconds;
            trait.totalProcessedBlocks += currentTask.blocksProcessed;
            trait.totalProcessingTime += (currentTask.timeSpent + (new Date().getTime() - startTime));

            if (nSeconds < 1 || currentTask.blocksProcessed == 0L)
            {
                trait.lastBlocksPerSec = currentTask.blocksProcessed;
            }
            else
            {
                trait.lastBlocksPerSec = currentTask.blocksProcessed / nSeconds;
            }

            trait.lastResult = "Path found (" + routeTrace.size() + ")";
            if (!trait.getCurrentAction().equals(en_CurrentAction.RANDOM_MOVEMENT))
                trait.setCurrentAction(en_CurrentAction.PATH_FOUND);
            trait.setLastPathCalc();
            destRef.getMessageManager.debugMessage(Level.INFO, "astarpath.iterate()|NPC:" + currentTask.npc.getId() + "|Path Found (" + locationArray.size() + ")|Requested: " + currentTask.requestedBy);
            destRef.getMessageManager.sendDebugMessage("destinations", "debug_messages.path_found", currentTask.npc, currentTask.npcTrait);

            if (destRef.debugTargets.size() > 0) {
                final ArrayList<Location> debugTrace = (ArrayList<Location>) locationArray.clone();
                for (DebugTarget debugOutput : destRef.debugTargets) {
                    if (debugOutput.getTargets().size() == 0 || debugOutput.getTargets().contains(currentTask.npc.getId())) {
                        if (((Player) debugOutput.targetSender).isOnline()) {
                            Player player = ((Player) debugOutput.targetSender);
                            for (int count = 1; count < (debugTrace.size() - 1); count++) {
                                if (player.getWorld().equals(debugTrace.get(count).getWorld())) {
                                    debugOutput.addDebugBlockSent(debugTrace.get(count), Material.REDSTONE_BLOCK);
                                }
                            }
                            debugOutput.addDebugBlockSent(debugTrace.get(0), Material.GOLD_BLOCK);
                            debugOutput.addDebugBlockSent(debugTrace.get(debugTrace.size() - 1), Material.DIAMOND_BLOCK);
                        }
                    }
                }
            }

            if (routeTrace != null) {
                for (Tile tLoc : routeTrace) {
                    tLoc.destroy();
                }
                routeTrace.clear();
            }

            CleanTask();
        }

        // 1.6
        if (path_Queue.size() > 0) {
            NextQueue();
            return;
        }
    }

    private boolean canContinue() {

        if (currentTask == null)
            return false;

        if (currentTask.npc == null) {
            CleanTask();
            return false;
        }

        if (!currentTask.npc.isSpawned()) {
            CleanTask();
            return false;
        }

        if (currentTask.npcTrait == null) {
            CleanTask();
            return false;
        }

        // check if open list is empty, if it is no path has been found
        if (currentTask.open.size() == 0) {
            currentTask.pathFindingResult = PathingResult.NO_PATH;
            return false;
        } else {
            if (currentTask != null) {
                StringBuilder b = new StringBuilder();
                b.append(currentTask.end_X - currentTask.start_X).append(currentTask.end_Y - currentTask.start_Y).append(currentTask.end_Z - currentTask.start_Z);
                if (currentTask.closed.containsKey(b.toString())) {
                    currentTask.pathFindingResult = PathingResult.SUCCESS;
                    return false;
                } else {
                    b = new StringBuilder();
                    b.append(currentTask.end_X - currentTask.start_X).append((currentTask.end_Y + 1) - currentTask.start_Y).append(currentTask.end_Z - currentTask.start_Z);
                    if (currentTask.closed.containsKey(b.toString())) {
                        currentTask.pathFindingResult = PathingResult.SUCCESS;
                        return false;
                    } else {
                        return true;
                    }
                }
            } else {
                return false;
            }
        }
    }

    private Tile getLowestFTile() {
        double f = 0;
        Tile drop = null;

        // get lowest F cost square
        for (Tile t : currentTask.open.values()) {
            if (f == 0) {
                t.calculateBoth(currentTask.start_X, currentTask.start_Y, currentTask.start_Z, currentTask.end_X, currentTask.end_Y, currentTask.end_Z, true);
                f = t.getF();
                drop = t;
            } else {
                t.calculateBoth(currentTask.start_X, currentTask.start_Y, currentTask.start_Z, currentTask.end_X, currentTask.end_Y, currentTask.end_Z, true);
                double posF = t.getF();
                if (posF < f) {
                    f = posF;
                    drop = t;
                }
            }
        }

        // drop from open list and add to closed

        currentTask.open.remove(drop.getUID());
        addToClosedList(drop);

        return drop;
    }

    private boolean isOnClosedList(Tile t) {
        return currentTask.closed.containsKey(t.getUID());
    }

    // pass in the current tile as the parent
    private void processAdjacentTiles(Tile current) {

        // set of possible walk to locations adjacent to current tile
        HashSet<Tile> possible = new HashSet<Tile>(26);

        currentTask.blocksProcessed++;

        for (byte x = -1; x <= 1; x++) {
            for (byte y = -1; y <= 1; y++) {
                for (byte z = -1; z <= 1; z++) {

                    if (x == 0 && y == 0 && z == 0) {
                        continue;// don't check current square
                    }

                    Tile t = new Tile((short) (current.getX() + x), (short) (current.getY() + y), (short) (current.getZ() + z), current);
                    Location l = new Location(currentTask.world, (currentTask.start_X + t.getX()), (currentTask.start_Y + t.getY()), (currentTask.start_Z + t.getZ()));
    
                    //Validate the current tile has 3 spaces open above.
                    if (y == 1)
                    {
                        if (l.clone().add(0,2,0).getBlock().getType().isSolid())
                            continue;
                    }
                    if (y == -1)
                    {
                        if (l.clone().add(x,2,z).getBlock().getType().isSolid())
                            continue;
                    }
    
                    Block b = l.getBlock();
                    if (currentTask.allowedPathBlocks.size() > 0) {
                        l = new Location(currentTask.world, (currentTask.start_X + t.getX()), (currentTask.start_Y + t.getY()), (currentTask.start_Z + t.getZ()));

                        b = l.getBlock();
                        if (bInWater && b.isLiquid()) {
                            // anything?
                        } else {
                            // Validate the block types
                            if (currentTask.getBlocksBelow() != 0) {
                                if (!currentTask.allowedPathBlocks.contains(currentTask.getPathLocation(l).getBlock().getType())) {
                                    continue;
                                }
                            } else if (!currentTask.allowedPathBlocks.contains(b.getType())) {
                                continue;
                            }
                        }
                    }

                    if (!t.isInRange(currentTask.range)) {
                        // if block is out of bounds continue
                        continue;
                    }

                    if (!bInWater && b.isLiquid()) {
                        continue;
                    }

                    if (x != 0 && z != 0 && (y == 0 || y == 1)) {
                        // check to stop jumping through diagonal blocks
                        Tile xOff = new Tile((short) (current.getX() + x), (short) (current.getY() + y), current.getZ(), current);
                        Tile zOff = new Tile(current.getX(), (short) (current.getY() + y), (short) (current.getZ() + z), current);
                        if (!isTileWalkable(xOff) && !isTileWalkable(zOff)) {
                            continue;
                        }
                    }
                    if (x != 0 && z != 0 && (y == 0 || y == -1)) {
                        // check to stop jumping through diagonal blocks
                        Tile xOff = new Tile((short) (current.getX() + x), (short) (current.getY() + y), current.getZ(), current);
                        Tile zOff = new Tile(current.getX(), (short) (current.getY() + y), (short) (current.getZ() + z), current);
                        if (!isTileWalkable(xOff) && !isTileWalkable(zOff)) {
                            continue;
                        }
                    }
                    
                    //Openables?
                    if (destRef.getMCUtils.isOpenable(b) || destRef.getMCUtils.isOpenable(b.getLocation().add(0, 1, 0).getBlock()))
                        if (x != 0 && z != 0)
                            continue;
                    
                    if (isOnClosedList(t)) {
                        // ignore tile
                        continue;
                    }

                    // only process the tile if it can be walked on
                    if (!isTileWalkable(t)) {
                        continue;
                    }
                    
                    

                    t.calculateBoth(currentTask.start_X, currentTask.start_Y, currentTask.start_Z, currentTask.end_X, currentTask.end_Y, currentTask.end_Z, true);
                    possible.add(t);
                }
            }
        }

        for (Tile t : possible) {
            // get the reference of the object in the array
            Tile openRef = null;
            if ((openRef = isOnOpenList(t)) == null) {
                // not on open list, so add
                addToOpenList(t, false);
            } else {
                // is on open list, check if path to that square is better using
                // G cost
                if (t.getG() < openRef.getG()) {
                    // if current path is better, change parent
                    openRef.setParent(current);
                    // force updates of F, G and H values.
                    openRef.calculateBoth(currentTask.start_X, currentTask.start_Y, currentTask.start_Z, currentTask.end_X, currentTask.end_Y, currentTask.end_Z, true);
                }

            }
        }

    }

    private Tile isOnOpenList(Tile t) {
        return (currentTask.open.containsKey(t.getUID()) ? currentTask.open.get(t.getUID()) : null);
    }

    private boolean isTileWalkable(Tile t) {
        return isTileWalkable(t,true);
    }
    
    private boolean isTileWalkable(Tile t, Boolean allowDoors) {
        Location l = new Location(currentTask.world, (currentTask.start_X + t.getX()), (currentTask.start_Y + t.getY()), (currentTask.start_Z + t.getZ()));
        if (!destRef.getMCUtils.isLocationWalkable(l, currentTask.opensGates, currentTask.opensWoodDoors, currentTask.opensMetalDoors))
            return false;

        Block b = l.getBlock();

        if (b.getRelative(0, 1, 0).getState().getData() instanceof Openable) {
            org.bukkit.block.BlockState oBlockState = b.getRelative(0, 1, 0).getState();

            if (destRef.getMCUtils.isGate(b.getRelative(0, 1, 0).getType())) {
                if (!allowDoors)
                    return false;
                if (currentTask.opensGates ) {
                    return true;
                } else {
                    Openable oOpenable = (Openable) oBlockState.getData();
                    return (oOpenable.isOpen() && (!b.getRelative(0, 2, 0).getType().isSolid()));
                }
            } else if (destRef.getMCUtils.isWoodDoor(b.getRelative(0, 1, 0).getType())) {
                if (!allowDoors)
                    return false;
                if (currentTask.opensWoodDoors) {
                    return true;
                } else {
                    Openable oOpenable = (Openable) oBlockState.getData();
                    return (oOpenable.isOpen() && (!b.getRelative(0, 2, 0).getType().isSolid()));
                }
            } else if (destRef.getMCUtils.isMetalDoor(b.getRelative(0, 1, 0).getType())) {
                if (!allowDoors)
                    return false;
                if (currentTask.opensMetalDoors) {
                    return true;
                } else {
                    Openable oOpenable = (Openable) oBlockState.getData();
                    return (oOpenable.isOpen() && (!b.getRelative(0, 2, 0).getType().isSolid()));
                }
            }
        }

        return true;
    }

    private void NextQueue() {
        if (currentTask != null)
            return;

        if (path_Queue == null)
            return;

        if (last_Pause > new Date().getTime())
            return;

        if (path_Queue.size() > 0) {
            Entry<Integer, PathFindingQueue> entryItem = path_Queue.entrySet().iterator().next();
            currentTask = entryItem.getValue();
            currentTask.npcTrait.setCurrentAction(en_CurrentAction.PATH_HUNTING);
            path_Queue.remove(entryItem.getKey());
            ProcessQueueItem();
            return;
        }
    }

    private void CleanTask() {
        if (destRef.debugLogLevel == Level.FINEST)
            destRef.getMessageManager.debugMessage(Level.FINEST, Arrays.toString(Thread.currentThread().getStackTrace()));

        if (currentTask.open != null) {
            for (Tile t : currentTask.open.values()) {
                t.destroy();
            }
            currentTask.open.clear();
        }

        if (currentTask.closed != null) {
            for (Tile t : currentTask.closed.values()) {
                t.destroy();
            }
            currentTask.closed.clear();
        }
        currentTask = null;
    }

}