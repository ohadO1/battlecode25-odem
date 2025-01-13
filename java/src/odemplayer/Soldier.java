package odemplayer;

import battlecode.common.*;

import java.util.ArrayList;

class Soldier extends Globals {

  enum SOLDIER_STATES{
    scoutDirection, //scout a specific direction as assigned by the tower.
    roam,           //walk randomly
    notifyTower,    //found a ruin im not able to build. walk to notify tower
    buildTower,     //found a ruin im able to build. build it.
    waitForRefill,  //wait for a tower to send a mopper for a refill.
    attack,         //found a tower with enough friends to attack it.
  }

  //tasks are like a macro state. they are persistent within states.
  //a soldier doesn't necessarily have a task at all times.
  static SOLDIER_TASKS task = null;
  enum SOLDIER_TASKS{
    buildTower,
    attack,
  }

  static SOLDIER_STATES state = SOLDIER_STATES.roam;
  static SOLDIER_STATES statePrev = state;
  static boolean stateChanged = false;

  /// state specific vars

  //build tower
  static MapInfo ruinDest = null;

  //notifyTower
  static MapLocation notifyDest;    //tower to notify ive found a ruin

  // TODO: attack state
  // TODO: scout direction state
  // TODO: search for enemy towers and call an attack

  public static void runSoldier(RobotController rc) throws GameActionException {

    Utils.updateFriendlyTowers(rc);

    switch (state) {

      //region roam
      case roam:

        //search for the closest ruin in range
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int currentDistance = 99999;
        for (MapInfo tile : nearbyTiles) {
          if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
            int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
            if (distance < currentDistance) {
              ruinDest = tile;
              currentDistance = distance;
            }
          }
        }

        //if found ruin, decide what to do about it.
        if (ruinDest != null) {
          if (false)  //shouldIBuild(findClosestTower(knownTowersLocations,rc),rc.getPaint())
            state = SOLDIER_STATES.buildTower;
          else
            state = SOLDIER_STATES.notifyTower;

          task = SOLDIER_TASKS.buildTower;
        }
        //if didn't find ruin, wander around.
        else {
          MapLocation nextLoc = Utils.roamGracefullyf(rc);

          if (rc.canAttack(nextLoc)) {
            MapInfo nextLocInfo = rc.senseMapInfo(nextLoc);
            if (!nextLocInfo.getPaint().isAlly()) {
              rc.attack(nextLoc);
            }
          }

        }
        break;
      //endregion
      //region build tower
      case SOLDIER_STATES.buildTower:

        if(stateChanged) System.out.println("starting to build tower at: " + ruinDest.getMapLocation());

        //go to the ruin
        MapLocation targetLocation = ruinDest.getMapLocation();
        Direction dir = rc.getLocation().directionTo(targetLocation);
        if (rc.canMove(dir)) {
          rc.move(dir);
        }

        //try to mark
        MapLocation checkMarked = targetLocation.subtract(dir);
        if (rc.senseMapInfo(checkMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
          rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
        }

        //loop through marks. attack whatever is possible
        boolean attacked = false;
        MapInfo[] patternTiles = rc.senseNearbyMapInfos(targetLocation, 8);
        for (int i = 0; i < patternTiles.length && !attacked; i++) {
          if (patternTiles[i].getMark() != patternTiles[i].getPaint() && patternTiles[i].getMark() != PaintType.EMPTY && rc.canAttack(patternTiles[i].getMapLocation())) {
            attacked = true;
            rc.attack(patternTiles[i].getMapLocation(), patternTiles[i].getMark() == PaintType.ALLY_SECONDARY);
          }
        }

        //complete tower building
        UnitType towerToBuild = null; //whatShouldIBuild(knownTowers,rc.getPaint(),targetLocation);
        if (towerToBuild != null) {
          rc.completeTowerPattern(towerToBuild, targetLocation);
          state = SOLDIER_STATES.roam;
        }

        break;
      //endregion
      //region notify tower
        case SOLDIER_STATES.notifyTower:

          notifyDest = Utils.findClosestTower(knownTowersInfos, rc);
          PathFinder.moveToLocation(rc, notifyDest);
          if(rc.canSendMessage(notifyDest)) {
            rc.sendMessage(notifyDest,Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere,notifyDest));

            if(rc.getPaint()/rc.getType().paintCapacity > SOLDIER_PAINT_FOR_TASK)
              state = SOLDIER_STATES.buildTower;
            else{
              Clock.yield();
              rc.sendMessage(notifyDest,Utils.encodeMessage(MESSAGE_TYPE.askForRefill,notifyDest));
              state = SOLDIER_STATES.waitForRefill;
            }

          }
        break;
        //endregion

    }

    //states
    stateChanged = state != statePrev;
    statePrev = state;
    if(stateChanged) System.out.println("state changed: " + state.name());

  }

}
