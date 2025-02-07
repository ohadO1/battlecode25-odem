package Bareket;

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
    private MapLocation spawnTowerLocation;

    // Splasher parameters
    private int splasherID;
    private int splasherIndex;
    private int totalSplashers;

    // Assigned Area
    private int AreaStartY;
    private int AreaEndY;
    private int AreaStartX;
    private int AreaEndX;
    private int AreaHeight;
    private MapLocation AreaCenter;
    private MapLocation randPointInArea;
    private MapLocation[] alreadyVisited = new MapLocation[30];
    MapLocation closeLocList;


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
                                currentTarget = randPointInArea;
                            }
                            // find and move to far point of the Splasher's area
                            if (currentLocation.equals(randPointInArea)
                                    || (rc.canSenseLocation(randPointInArea)
                                    && rc.senseMapInfo(randPointInArea).isWall())) {
                                state = SPLASHER_STATE.attackArea;
                                currentTarget = null;
                                break;
                            } else {
                                moveToLocation(rc, randPointInArea);
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
                                if (rc.getMapHeight() > 30) {updateTargetAreaBigMaps();}
                                 else {updateTargetArea();}
                                state = SPLASHER_STATE.moveToArea;
                            }
                            break;
                        case seekRefill: 
                            int missingPaint = rc.getType().paintCapacity - rc.getPaint();
                            refillTower = null;
                            for(RobotInfo tower : knownTowersInfos){
                                if(refillTower == null ||
                                    (tower.getLocation().distanceSquaredTo(rc.getLocation()) < refillTower.getLocation().distanceSquaredTo(rc.getLocation()))
                                    && tower.getPaintAmount() >= missingPaint)
                                refillTower = tower;
                            }
                            //no tower has enough paint. just go to the nearest and wait there.
                            if(refillTower == null) refillTower = Utils.findClosestTowerInfo(knownTowersInfos,rc);
                            
                            //move
                            PathFinder.moveToLocation(rc,refillTower.getLocation());
                            //am i there yet?

                            try{
                                if(rc.getLocation().distanceSquaredTo(refillTower.getLocation()) <= 2){

                                    if(!rc.canTransferPaint(refillTower.getLocation(),-missingPaint)){
                                        for(int i = 0; i < alreadyVisited.length; i++){
                                            if(alreadyVisited[i] == null){
                                                alreadyVisited[i] = refillTower.getLocation();
                                                break;
                                            }
                                        } 
                                        closeLocList = closestTower(rc.getLocation() , alreadyVisited);
                                        moveToLocation(rc, closeLocList);
                                    } else{
                                        rc.transferPaint(refillTower.getLocation(),-missingPaint);
                                        //for(int i = 0; i < alreadyVisited.length; i++){
                                        //alreadyVisited[i] = null;
                                        //    }
                                        state = SPLASHER_STATE.returnToArea;
                                    }
                                }
                            
                            } catch (GameActionException e) {
                                System.out.println("Error transferring paint: " + e.getMessage());
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
        spawnTowerLocation = findNearestAllyPaintTower(rc);
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
    private void assignArea2(RobotController rc) {
        // Divide the map to horizontal bands
        AreaHeight = mapHeight / totalSplashers;
        AreaStartY = splasherIndex * AreaHeight;
        AreaEndY = (splasherIndex == totalSplashers - 1) ? mapHeight - 1 : (AreaStartY + AreaHeight - 1);

        // Define Area center and farthest point
        // Assuming spawn area is at the bottom (y = 0)
        AreaCenter = new MapLocation(mapWidth / 2, (AreaStartY + AreaEndY) / 2);
//TODO: Change this stupid logic 
        randPointInArea = new MapLocation(mapWidth / 2, AreaEndY);
        // Adjust for walls
        try {
            while (rc.onTheMap(randPointInArea) &&rc.canSenseLocation(randPointInArea)&& rc.senseMapInfo(randPointInArea).isWall()) {
                randPointInArea = randPointInArea.translate(0, -1);
                if (randPointInArea.y < 0) {
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
        MapLocation loc = rc.getLocation();
        if (isBadLand()&&rc.canAttack(loc)) {
            rc.attack(loc);
        }
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
        if (potentialTarget.equals(randPointInArea)) {
            continue;
        }

        if (rc.onTheMap(potentialTarget) && rc.canSenseLocation(potentialTarget)&&!rc.senseMapInfo(potentialTarget).isWall()) {
            if (areaNeedsRepainting(potentialTarget)) {
                randPointInArea = potentialTarget;
                currentTarget = randPointInArea;
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
            if (potentialTarget.equals(randPointInArea)) {
                continue;
            }
            if (rc.onTheMap(potentialTarget) &&rc.canSenseLocation(potentialTarget)&& !rc.senseMapInfo(potentialTarget).isWall()) {
                if (areaNeedsRepainting(potentialTarget)) {
                    randPointInArea = potentialTarget;
                    currentTarget = randPointInArea;
                    newTargetFound = true;
                    break;
                }
            }
        }
    }

    if (!newTargetFound) {
//TODO- CHANGE THE LOGIC: If still no new target found get to a new location
        randPointInArea = AreaCenter;
        currentTarget = randPointInArea;
    }
}
private void updateTargetAreaBigMaps() throws GameActionException {
    int searchRange = Math.max(mapHeight, mapWidth); 
    for (int range = 0; range <= searchRange; range += 5) {
        for (int dy = -range; dy <= range; dy += 5) {
            int newY = AreaCenter.y + dy;
            if (newY == randPointInArea.y || newY < 0 || newY >= mapHeight) {
                continue;
            }
            MapLocation potentialTarget = new MapLocation(AreaCenter.x, newY);
            if (rc.onTheMap(potentialTarget) && rc.canSenseLocation(potentialTarget)&&!rc.senseMapInfo(potentialTarget).isWall()) {
                if (areaNeedsRepainting(potentialTarget)) {
                    // Set the new target area
                    randPointInArea = potentialTarget;
                    currentTarget = randPointInArea;
                    rc.setIndicatorString("New target area selected at: " + randPointInArea);
                    return;
                }
            }
        }
    }
    MapLocation ExploreLoc = getRandomLocationNearby();
    MapLocation RandomLoc = getRandomLocationOnEdge();
    randPointInArea = (ExploreLoc == rc.getLocation()) ? RandomLoc : ExploreLoc;
    currentTarget = randPointInArea;
    return;
}
private MapLocation getRandomLocationOnEdge() {
    int edge = rng.nextInt(4); // Pick a random region
    int x = 0, y = 0;
    switch (edge) {
        case 0:
            x = rng.nextInt(mapWidth); // Any x value
            y = rng.nextInt(mapHeight / 4); // Bottom quarter of the map
            break;
        case 1:
            x = rng.nextInt(mapWidth); // Any x value
            y = mapHeight - 1 - rng.nextInt(mapHeight / 4); // Top quarter
            break;
        case 2:
            x = rng.nextInt(mapWidth / 4); // Left quarter of the map
            y = rng.nextInt(mapHeight); // Any y value
            break;
        case 3:
            x = mapWidth - 1 - rng.nextInt(mapWidth / 4); // Right quarter
            y = rng.nextInt(mapHeight); // Any y value
            break;}
    return new MapLocation(x, y);
}
private MapLocation getRandomLocationNearby() {
    MapLocation myLocation = rc.getLocation();
    int attempts = 10; 
    for (int i = 0; i < attempts; i++) {
        int dx = rng.nextBoolean() ? 9 :-9;
        int dy = rng.nextBoolean() ? 9 :-9;
        int newX = myLocation.x + dx;
        int newY = myLocation.y + dy;
        MapLocation potentialLocation = new MapLocation(newX, newY);
        try{
            if (rc.onTheMap(potentialLocation) && rc.canSenseLocation(potentialLocation)&&!rc.senseMapInfo(potentialLocation).isWall()) {
                if (areaNeedsRepainting(potentialLocation)) {
                    return potentialLocation;
                }
            }
        } catch (GameActionException e) {
            System.out.println("Error getting random location: " + e.getMessage());

        }
    }
    return myLocation;
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
        /*MapLocation loc = info.getMapLocation();

        if (!isInAssignedArea(loc)) {
            continue;
        }*/
        PaintType paint = info.getPaint();
        if (paint == PaintType.EMPTY || paint.isEnemy()) {
            unpaintedTiles++;
            if (unpaintedTiles > 4) {
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
            if (tilesPainted <= 0) { 
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
                        score += 20;
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
        if(bestScore <5){
            assignArea(rc);
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
    private boolean isBadLand(){
        int badLand=0;
        try{
        for (Direction dir : directions) {
            MapLocation adjacentLocation = rc.getLocation().add(dir);
            if (rc.onTheMap(adjacentLocation)) { // Ensure the location is within the map
                PaintType paint = rc.senseMapInfo(adjacentLocation).getPaint();
                if (paint.isEnemy()) {
                    badLand++;
                }
            }

        }
        PaintType currentPaint = rc.senseMapInfo(rc.getLocation()).getPaint();
        if (currentPaint.isEnemy()) {
            badLand++;
        }
        return (badLand > 5);
        } catch (GameActionException e) {
            System.out.println("Error checking bad land: " + e.getMessage());
            return false;
        }
    }
    private void assignArea(RobotController rc){
        try{
        int areaIndex = rng.nextInt(8);
        switch(areaIndex){
            case 0:
                AreaStartY = 0;
                AreaEndY = mapHeight/3;
                AreaStartX = 0;
                AreaEndX = mapWidth/3;
                AreaCenter = new MapLocation(mapWidth/6,mapHeight/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 1:
                AreaStartY = 0;
                AreaEndY = mapHeight/3;
                AreaStartX = mapWidth/3;
                AreaEndX = mapWidth*2/3;
                AreaCenter = new MapLocation(mapWidth/2,mapHeight/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 2:
                AreaStartY = 0;
                AreaEndY = mapHeight/3;
                AreaStartX = mapWidth*2/3;
                AreaEndX = mapWidth;
                AreaCenter = new MapLocation(mapWidth*5/6,mapHeight/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 3:
                AreaStartY = mapHeight/3;
                AreaEndY = mapHeight*2/3;
                AreaStartX = 0;
                AreaEndX = mapWidth/3;
                AreaCenter = new MapLocation(mapWidth/6,mapHeight/2);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 5:
                AreaStartY = mapHeight/3;
                AreaEndY = mapHeight*2/3;
                AreaStartX = mapWidth*2/3;
                AreaEndX = mapWidth;
                AreaCenter = new MapLocation(mapWidth*5/6,mapHeight/2);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 6:
                AreaStartY = mapHeight*2/3;
                AreaEndY = mapHeight;
                AreaStartX = 0;
                AreaEndX = mapWidth/3;
                AreaCenter = new MapLocation(mapWidth/6,mapHeight*5/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 7:
                AreaStartY = mapHeight*2/3;
                AreaEndY = mapHeight;
                AreaStartX = mapWidth/3;
                AreaEndX = mapWidth*2/3;
                AreaCenter = new MapLocation(mapWidth/2,mapHeight*5/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);

                break;
            case 4:
                AreaStartY = mapHeight*2/3;
                AreaEndY = mapHeight;
                AreaStartX = mapWidth*2/3;
                AreaEndX = mapWidth;
                AreaCenter = new MapLocation(mapWidth*5/6,mapHeight*5/6);
                randPointInArea = getRandomPointInAssignedArea(AreaStartX ,AreaEndX ,AreaStartY,AreaEndY);
                break;
            }
         }catch (Exception e) {
                System.out.println("Error adjusting farthest point: " + e.getMessage());
            }        
        

    }
    private MapLocation getRandomPointInAssignedArea(int x1 ,int x2 ,int y1,int y2) {
        int width  = x2 - x1;
        int height = y2 - y1;
    
        if (width < 1 || height < 1) {
            return new MapLocation(AreaStartX, AreaStartY);
        }
        int randX = AreaStartX + rng.nextInt(width);
        int randY = AreaStartY + rng.nextInt(height);
    
        return new MapLocation(randX, randY);
    }
    private MapLocation closestTower(MapLocation loc, MapLocation[] alreadyVisited){
        double[] dist = new double[knownTowersInfos.size()];
        int i = 0;
        double closestSoFar = Integer.MAX_VALUE;
        MapLocation closestTower = spawnTowerLocation;
        for( RobotInfo towerInf : knownTowersInfos){
            for(MapLocation alreadyVisitedLoc : alreadyVisited){
                if(towerInf.getLocation().equals(alreadyVisitedLoc)){
                    continue;
                }
            }
                MapLocation tower = towerInf.getLocation();
                dist[i] = Math.sqrt((tower.x - loc.x) * (tower.x - loc.x) + (tower.y - loc.y) * (tower.y - loc.y));
                if(dist[i]< closestSoFar&&(!(dist[i]<2))){
                    closestSoFar = dist[i];
                    closestTower = tower;
                }
                i++;

        } return closestTower;
        
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
/*
 * //TODO:::: it's a smarter version but to test that it's really better. older version is commented below
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
                                moveToLocation(rc, spawnTowerLocation);
                            }
                            break;
 */
