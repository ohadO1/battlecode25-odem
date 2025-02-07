package odemplayer_tournament1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

class Soldier extends Globals {

  enum SOLDIER_STATES{
    scoutDirection, //scout a specific direction as assigned by the tower.
    roam,           //walk randomly
    notifyTower,    //found a ruin im not able to build. walk to notify tower
    buildTower,     //found a ruin im able to build. build it.
    attack,         //found a tower with enough friends to attack it.
  }

  static SOLDIER_STATES state = SOLDIER_STATES.roam;

  // TODO: too long and clumsy - organize
  public static void runSoldier(RobotController rc) throws GameActionException {

    switch (state){

        //region Roam
        case roam:

        //search for the closest ruin in range
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapInfo currentRuin = null;
        int currentDistance = 99999;

        for (MapInfo tile : nearbyTiles) {
          if (tile.hasRuin()) {
            int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
            if (distance < currentDistance) {
              currentRuin = tile;
              currentDistance = distance;
            }
          }
        }


        //if found ruin, go there.
        if (currentRuin != null) {
          MapLocation targetLocation = currentRuin.getMapLocation();
          Direction dir = rc.getLocation().directionTo(targetLocation);
          if (rc.canMove(dir)) {
            rc.move(dir);
          }

          MapLocation checkMarked = targetLocation.subtract(dir);
          if (rc.senseMapInfo(checkMarked).getMark() == PaintType.EMPTY
                  && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
          }

          for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLocation, 8)) {
            if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY) {
              if (rc.canAttack(patternTile.getMapLocation())) {
                if (patternTile.getMark() == PaintType.ALLY_SECONDARY) {
                  rc.attack(patternTile.getMapLocation(), true);
                } else {
                  rc.attack(patternTile.getMapLocation(), false);
                }
              }
            }
          }

          if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
          }

        }

        MapLocation nextLoc = Utils.roamGracefullyf(rc);

        if (rc.canAttack(nextLoc)) {
          MapInfo nextLocInfo = rc.senseMapInfo(nextLoc);
          if (!nextLocInfo.getPaint().isAlly()) {
            rc.attack(nextLoc);
          }
        }
        break;
        //endregion


    }




  }

}
