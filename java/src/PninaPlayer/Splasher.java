package PninaPlayer;

import battlecode.common.*;
import java.util.ArrayList;
import java.util.Collections;

public class Splasher extends Globals {
    private enum SPLASHER_STATE {
        moveToArea,
        attackArea,
        seekRefill,
        returnToArea
    }

    private SPLASHER_STATE state = SPLASHER_STATE.moveToArea;
    private SPLASHER_STATE prevState = state;
    private boolean stateChanged = false;

    // Map parameters
    private int mapWidth;
    private int mapHeight;

    // Splasher parameters
    private int splasherID;
    private int splasherIndex;
    private int totalSplashers;

    // Assigned Area
    private int AreaStartY;
    private int AreaEndY;
    private int AreaHeight;
    private MapLocation AreaCenter;
    private MapLocation farthestPointInArea;

    // Paint thresholds
    private static final double PAINT_THRESHOLD_ATTACK = 50; 
    private static final double PAINT_THRESHOLD_REFILL = 100; 
    private static final double SOLDIER_PAINT_FOR_CASUAL_REFILL = 0.5; 

    // Target variables
    private MapLocation currentTarget;
    private MapLocation nearestAllyPaintTower;
    private RobotInfo refillTower;

    private RobotController rc;

    public static void runSplasher(RobotController rc) throws GameActionException {
        Splasher splasher = new Splasher(rc);
        splasher.run();
    }

    public Splasher(RobotController rc) {
        this.rc = rc;
    }
    public void run() {
        try {
            Utils.updateFriendlyTowers(rc);
            initializeSplasher(rc); 
            while (true) {
                try {
                    stateChanged = false;
                    double currentPaint = rc.getPaint();
                    MapLocation currentLocation = rc.getLocation();

                    //  update splasherInfo every 25 rounds
                    if (rc.getRoundNum() % 25 == 0) {
                        updateSplasherInfo(rc);
                    }
                    switch (state) {
                        case moveToArea:
                            if (currentTarget == null) {
                                currentTarget = farthestPointInArea;
                            }
                            // find and move to far point of the Splasher's area
                            if (currentLocation.equals(farthestPointInArea)
                                    || (rc.canSenseLocation(farthestPointInArea)
                                    && rc.senseMapInfo(farthestPointInArea).isWall())) {
                                state = SPLASHER_STATE.attackArea;
                                currentTarget = null;
                                break;
                            } else {
                                moveToLocation(rc, farthestPointInArea);
                            }
                            if (currentPaint < PAINT_THRESHOLD_REFILL) {
                                state = SPLASHER_STATE.seekRefill;
                            }
                            break;

                        case attackArea:
                            if (currentPaint < PAINT_THRESHOLD_ATTACK) {
                                state = SPLASHER_STATE.seekRefill;
                                break;
                            }
                            if (rc.isActionReady()) {
                                MapLocation bestAttackLocation = findBestAttackLocation(rc);
                                if (bestAttackLocation != null) {
                                    rc.attack(bestAttackLocation);
                                } else {
                                    // No optimal location found
                                    moveWithinArea(rc);
                                }
                            } else if (rc.isMovementReady()) {
                                moveWithinArea(rc);
                            }
                            // Transition to moveToArea if needed
                            if (needsToMoveToNextArea(rc)) {
                                updateTargetArea(); // Update target to a new area further away
                                state = SPLASHER_STATE.moveToArea;
                            }
                            break;
                        case seekRefill: 
//TODO:::: it's a smarter version but to test that it's really better. older version is commented below
                            nearestAllyPaintTower = findNearestAllyPaintTower(rc);
                            if (nearestAllyPaintTower != null) {
                                moveToLocation(rc, nearestAllyPaintTower);
                                int paintNeeded = (int) (rc.getType().paintCapacity - currentPaint);
                                if (rc.canTransferPaint(nearestAllyPaintTower, -paintNeeded)) {
                                    rc.transferPaint(nearestAllyPaintTower, -paintNeeded);
                                    state = SPLASHER_STATE.returnToArea;
                                    currentTarget = null;
                                }
                            } else {
//TODO change this to go back to spawn tower or something
                                moveWithinArea(rc);
                            }
                            break;

                        case returnToArea:
                            if (isInAssignedArea(rc.getLocation())) {
                                state = SPLASHER_STATE.attackArea;
                            } else {
                                moveToLocation(rc, AreaCenter);
                            }
                            break;

                        default:
                            state = SPLASHER_STATE.moveToArea;
                            break;
                    }
                    stateChanged = (prevState != state);
                    if(stateChanged){
                        rc.setIndicatorString("State changed from " + prevState + " to " + state);
                    }
                    prevState = state;
                } catch (Exception e) {
                    System.out.println("Splasher Exception");
                    e.printStackTrace();
                    rc.setIndicatorString("Exception: " + e.getMessage());
                } finally {
                    Clock.yield();
                }
            }
        } catch (Exception e) {
            System.out.println("Splasher Initialization Exception");
            e.printStackTrace();
            rc.setIndicatorString("Initialization Exception: " + e.getMessage());
        }
    }

    /**
     * Initializes the Splasher's parameters and assigned Area.
     *
     * @param rc The RobotController instance.
     * @throws GameActionException
     */
    private void initializeSplasher(RobotController rc) throws GameActionException {
        splasherID = rc.getID();
        totalSplashers = 1; // Start with 1 just to prevent division by zero
        splasherIndex = 0;
        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        AreaStartY = 0;
        AreaEndY = mapHeight - 1;
        updateSplasherInfo(rc); // Method to update totalSplashers and splasherIndex dynamically
    }

    /**
     * Updates Splasher information, including total number of Splashers and index.
     *
     * @param rc The RobotController instance.
     * @throws GameActionException
     */
    private void updateSplasherInfo(RobotController rc) throws GameActionException {
        // Sense nearby allied Splashers
        int commRange = 20; // TODO: can be better logically
        RobotInfo[] allies = rc.senseNearbyRobots(commRange, rc.getTeam());
        ArrayList<Integer> splasherIDs = new ArrayList<>();
        splasherIDs.add(rc.getID()); // Include self

        for (RobotInfo ally : allies) {
            if (ally.getType() == UnitType.SPLASHER) {
                splasherIDs.add(ally.getID());
            }
        }

        // Sort IDs to assign consistent indices and determin index
        Collections.sort(splasherIDs);
        splasherIndex = splasherIDs.indexOf(rc.getID());
        totalSplashers = splasherIDs.size();
        if (totalSplashers < 1) { //for division by zero
            totalSplashers = 1;
        }
        // Update Area
        assignArea(rc);
    }
    /**
     * Assigns a Area to the Splasher based on its index and total number of Splashers.
     *
     * @param rc The RobotController instance.
     * @throws GameActionException
     */
    private void assignArea(RobotController rc) {
        // Divide the map to horizontal bands
        AreaHeight = mapHeight / totalSplashers;
        AreaStartY = splasherIndex * AreaHeight;
        AreaEndY = (splasherIndex == totalSplashers - 1) ? mapHeight - 1 : (AreaStartY + AreaHeight - 1);

        // Define Area center and farthest point
        // Assuming spawn area is at the bottom (y = 0)
        AreaCenter = new MapLocation(mapWidth / 2, (AreaStartY + AreaEndY) / 2);
//TODO: Change this stupid logic 
        farthestPointInArea = new MapLocation(mapWidth / 2, AreaEndY);
        // Adjust for walls
        try {
            while (rc.onTheMap(farthestPointInArea) && rc.senseMapInfo(farthestPointInArea).isWall()) {
                farthestPointInArea = farthestPointInArea.translate(0, -1);
                if (farthestPointInArea.y < 0) {
                    break; 
                }
            }
        } catch (GameActionException e) {
            System.out.println("Error adjusting farthest point: " + e.getMessage());
        }
//TODO: check and then remove
        rc.setIndicatorString("Area Y: " + AreaStartY + " to " + AreaEndY); 
    }
    /**
     * Moves the Splasher towards the specified location using pathfinding.
     *
     * @param rc             The RobotController instance.
     * @param targetLocation The target MapLocation to move towards.
     * @throws GameActionException
     */
    private void moveToLocation(RobotController rc, MapLocation targetLocation) throws GameActionException {
        if (rc.isMovementReady() && rc.canMove(rc.getLocation().directionTo(targetLocation))) {
            PathFinder.moveToLocation(rc, targetLocation);
        } else {
            moveWithinArea(rc); // Try moving within Area
        }
    }

    /**
     * Determines if the Splasher is within its assigned Area.
     *
     * @param location The MapLocation to check.
     * @return True if within assigned Area, false otherwise.
     */
    private boolean isInAssignedArea(MapLocation location) {
        return location.y >= AreaStartY && location.y <= AreaEndY;
    }

    /**
     * Finds the best attack location within attack range that maximizes painting impact.
     *
     * @param rc The RobotController instance.
     * @return The MapLocation to attack, or null if none found.
     * @throws GameActionException
     */

 /**
 * Updates the target area to a new location within the assigned Area that needs repainting.
 */
private void updateTargetArea() throws GameActionException {
    int searchStartY = AreaStartY;
    int searchEndY = AreaEndY;
    boolean newTargetFound = false;
    int stepSize = 5; // Can be just the entire Area height. Smaller values are more precise but slower.

    // Search for unpainted or enemy-painted areas within the assigned Area
    for (int y = searchStartY; y <= searchEndY; y += stepSize) {
        int adjustedY = y;
        if (adjustedY < 0) {
            adjustedY = 0;
        } else if (adjustedY >= mapHeight) {
            adjustedY = mapHeight - 1;
        }
        MapLocation potentialTarget = new MapLocation(AreaCenter.x, adjustedY);

        // Check if the potential target is not the current farthest point to prevent oscillation
        if (potentialTarget.equals(farthestPointInArea)) {
            continue;
        }

        if (rc.onTheMap(potentialTarget) && !rc.senseMapInfo(potentialTarget).isWall()) {
            if (areaNeedsRepainting(potentialTarget)) {
                farthestPointInArea = potentialTarget;
                currentTarget = farthestPointInArea;
                newTargetFound = true;
                break;
            }
        }
    }

    if (!newTargetFound) {
        // If no new target area was found, search in reverse order
        for (int y = searchEndY; y >= searchStartY; y -= stepSize) {
            int adjustedY = y;
            if (adjustedY < 0) {
                adjustedY = 0;
            } else if (adjustedY >= mapHeight) {
                adjustedY = mapHeight - 1;
            }

            MapLocation potentialTarget = new MapLocation(AreaCenter.x, adjustedY);
            if (potentialTarget.equals(farthestPointInArea)) {
                continue;
            }
            if (rc.onTheMap(potentialTarget) && !rc.senseMapInfo(potentialTarget).isWall()) {
                if (areaNeedsRepainting(potentialTarget)) {
                    farthestPointInArea = potentialTarget;
                    currentTarget = farthestPointInArea;
                    newTargetFound = true;
                    break;
                }
            }
        }
    }

    if (!newTargetFound) {
//TODO- CHANGE THE LOGIC: If still no new target found, default to roaming within the Area 
        farthestPointInArea = AreaCenter;
        currentTarget = farthestPointInArea;
    }
}

/**
 * Determines if the area around a given location needs repainting.
 *
 * @param location The MapLocation to check.
 * @return True if the area needs repainting, false otherwise.
 * @throws GameActionException
 */
private boolean areaNeedsRepainting(MapLocation location) throws GameActionException {
    MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(location);

    int unpaintedTiles = 0;
    for (MapInfo info : nearbyMapInfos) {
        MapLocation loc = info.getMapLocation();

        if (!isInAssignedArea(loc)) {
            continue;
        }

        PaintType paint = info.getPaint();
        if (paint == PaintType.EMPTY || paint.isEnemy()) {
            unpaintedTiles++;
            if (unpaintedTiles > 5) {
                return true;
            }
        }
    }
    return false;
}
    private MapLocation findBestAttackLocation(RobotController rc) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        MapLocation bestLocation = null;
        int maxTilesPainted = 0;

        for (Direction dir : directions) {
            if (!rc.canAttack(currentLocation.add(dir))) {
                continue;
            }
            if (!isInAssignedArea(currentLocation.add(dir))) {
                continue;
            }
            int tilesPainted = calculateTilesPainted(rc, currentLocation.add(dir));
            if (tilesPainted <= 2) { 
//TODO: Check if a different value is better
                continue;
            }
            if (tilesPainted > maxTilesPainted) {
                maxTilesPainted = tilesPainted;
                bestLocation = currentLocation.add(dir);
            }
        }

        return bestLocation;
    }

    /**
     * Calculates the number of tiles that would be painted by attacking a target location.
     *
     * @param rc             The RobotController instance.
     * @param targetLocation The MapLocation to simulate the attack.
     * @return The number of tiles that would be painted.
     * @throws GameActionException
     */
    private int calculateTilesPainted(RobotController rc, MapLocation targetLocation) throws GameActionException {
        int tilesPainted = 0;
        MapLocation[] affectedLocations = rc.getAllLocationsWithinRadiusSquared(targetLocation, rc.getType().actionRadiusSquared);

        for (MapLocation loc : affectedLocations) {
            if (!rc.canSenseLocation(loc)) {
                continue;
            }

            if (!isInAssignedArea(loc)) {
                continue;
            }

            MapInfo mapInfo = rc.senseMapInfo(loc);
            PaintType paint = mapInfo.getPaint();

            if (paint == PaintType.EMPTY || paint.isEnemy()) {
                tilesPainted++;
            }
        }
        return tilesPainted;
    }

    /**
     * Moves within the assigned Area, preferring unpainted or enemy-painted tiles.
     *
     * @param rc The RobotController instance.
     * @throws GameActionException
     */
    private void moveWithinArea(RobotController rc) throws GameActionException { 
//TODO: can be improved
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation currentLocation = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                MapLocation nextLoc = currentLocation.add(dir);

                if (!isInAssignedArea(nextLoc)) {
                    continue;
                }

                if (rc.canSenseLocation(nextLoc)) {
                    MapInfo nextInfo = rc.senseMapInfo(nextLoc);
                    if (nextInfo.isWall()) {
                        continue;
                    }

                    PaintType paint = nextInfo.getPaint();
                    int score = 0;

                    if (paint == PaintType.EMPTY) {
                        score += 10;
                    } else if (paint.isEnemy()) {
                        score += 8;
                    } else {
                        score += 1;
                    }
                    // Avoid enemy robots
                    RobotInfo robotAtLocation = rc.senseRobotAtLocation(nextLoc);
                    if (robotAtLocation != null && robotAtLocation.getTeam() == rc.getTeam().opponent()) {
                        continue;
                    }
                    // Random to break ties
                    score += rng.nextInt(3);

                    if (score > bestScore) {
                        bestScore = score;
                        bestDir = dir;
                    }
                }
            }
        }
        if (bestDir != null) {
            rc.move(bestDir);
        }
    }

    /**
     * Checks if the Splasher needs to move to the next area within the Area.
     *
     * @param rc The RobotController instance.
     * @return True if needs to move, false otherwise.
     * @throws GameActionException
     */
    private boolean needsToMoveToNextArea(RobotController rc) throws GameActionException {
        int unpaintedTiles = 0;
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();
        for (MapInfo info : nearbyMapInfos) {
            MapLocation loc = info.getMapLocation();
            if (!isInAssignedArea(loc)) {
                continue;
            }
            PaintType paint = info.getPaint();
            if (paint == PaintType.EMPTY || paint.isEnemy()) {
                unpaintedTiles++;
                if (unpaintedTiles > 3) { 
//TODO: Check if a different value is better
                    return false; 
                }
            }
        }
//TODO: If two or fewer tiles remain maybe leave mark for solider
        return true; // Move to a new area
    }

    /**
     * Finds the nearest allied paint tower to refill paint.
     *
     * @param rc The RobotController instance.
     * @return The MapLocation of the nearest allied paint tower, or null if none found.
     * @throws GameActionException
     */
    private MapLocation findNearestAllyPaintTower(RobotController rc) throws GameActionException {
        // Update known towers
        Utils.updateFriendlyTowers(rc);

        MapLocation myLocation = rc.getLocation();
        MapLocation nearestPaintTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo towerInfo : knownTowersInfos) {
            UnitType towerType = towerInfo.getType();
            if (towerType == UnitType.LEVEL_ONE_PAINT_TOWER ||
                    towerType == UnitType.LEVEL_TWO_PAINT_TOWER ||
                    towerType == UnitType.LEVEL_THREE_PAINT_TOWER) {

                int distance = myLocation.distanceSquaredTo(towerInfo.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPaintTower = towerInfo.getLocation();
                }
            }
        }

        return nearestPaintTower;
    }
}
/*
 *             case seekRefill:
                //abort
                if((double)rc.getPaint()/rc.getType().paintCapacity > SOLDIER_PAINT_FOR_CASUAL_REFILL)
                    state = SPLASHER_STATE.roam;
                int missingPaint = rc.getType().paintCapacity - rc.getPaint();
                if(stateChanged || refillTower == null) {
                stateChangedRunAt = rc.getRoundNum();
        //          System.out.print("looking for tower ... ");
                refillTower = null;

                for(RobotInfo tower : knownTowersInfos){
                    if(refillTower == null ||
                        (tower.getLocation().distanceSquaredTo(rc.getLocation()) < refillTower.getLocation().distanceSquaredTo(rc.getLocation()))
                        && tower.getPaintAmount() >= missingPaint)
                    refillTower = tower;
                }
                //no tower has enough paint. just go to the nearest and wait there.
                if(refillTower == null) refillTower = Utils.findClosestTowerInfo(knownTowersInfos,rc);
                }
                if(refillTower == null) System.out.println("still didnt find any tower. state changed run at: " + stateChangedRunAt);
                //move
                PathFinder.moveToLocation(rc,refillTower.getLocation());
                //am i there yet?
                if(!rc.canTransferPaint(refillTower.getLocation(),0)){

                if(rc.canTransferPaint(refillTower.getLocation(),-missingPaint))
                {
                    rc.transferPaint(refillTower.getLocation(),-missingPaint);
                    state = SPLASHER_STATE.roam;
                }
                }

      break;
 */