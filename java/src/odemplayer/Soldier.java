package odemplayer;

import battlecode.common.*;

import java.util.ArrayList;

class Soldier extends Globals {

  enum SOLDIER_STATES{
    scoutDirection, //scout a specific direction as assigned by the tower.
    roam,           //walk randomly
    notifyTower,    //found a ruin im not able to build. walk to notify tower
    buildTower,     //found a ruin im able to build. build it.
    refillFromTower,//go to a tower to refill paint.
    attack,         //found a tower with enough friends to attack it.
  }

  static SOLDIER_STATES state = SOLDIER_STATES.roam;
  static MapInfo ruinDest = null;
  static ArrayList<MapLocation> knownTowersLocations = new ArrayList<>();
  static MapLocation dest;                                //multipurpose var for going to places, used differently in different states.

  // TODO: refill from tower state
  // TODO: attack state
  // TODO: scout direction state
  // TODO: run sense towers to, uh, sense towers
  // TODO: optimize sense towers function below
  public static void runSoldier(RobotController rc) throws GameActionException {

    switch (state) {

      //region roam
      case roam:

        //search for the closest ruin in range
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int currentDistance = 99999;
        for (MapInfo tile : nearbyTiles) {
          if (tile.hasRuin()) {
            int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
            if (distance < currentDistance) {
              ruinDest = tile;
              currentDistance = distance;
            }
          }
        }

        //TODO: search for enemy towers and call an attack
        //if found ruin, decide what to do about it.
        if (ruinDest != null) {
          if (true)  //shouldIBuild(findClosestTower(knownTowersLocations,rc),rc.getPaint())
            state = SOLDIER_STATES.buildTower;
          else
            state = SOLDIER_STATES.notifyTower;
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

          dest = Utils.findClosestTower(knownTowersLocations, rc);
          PathFinder.moveToLocation(rc,dest);
          if(rc.canSendMessage(dest)) {
            rc.sendMessage(dest,encodeMessage(MESSAGE_TYPE.buildTowerHere,ruinDest));

            //if its a paint tower, goto refill paint
            RobotInfo tower = rc.senseRobotAtLocation(dest);
            if(tower.getTeam() == rc.getTeam()
                    && (tower.getType().equals(UnitType.LEVEL_ONE_PAINT_TOWER)
                      || tower.getType().equals(UnitType.LEVEL_TWO_PAINT_TOWER))
                      || tower.getType().equals(UnitType.LEVEL_THREE_PAINT_TOWER)) {
              state = SOLDIER_STATES.refillFromTower;
            }
            //else, look for a paint tower to refill from
            else {



            }

            state = SOLDIER_STATES.buildTower;
          }
        break;
        //endregion
    }




  }

  //run this every once in a while to sense towers
  private void senseTowers(RobotController rc){

    ArrayList<UnitType> towerTypes = new ArrayList<>();
    towerTypes.add(UnitType.LEVEL_ONE_PAINT_TOWER);
    towerTypes.add(UnitType.LEVEL_TWO_PAINT_TOWER);
    towerTypes.add(UnitType.LEVEL_THREE_PAINT_TOWER);
    towerTypes.add(UnitType.LEVEL_ONE_DEFENSE_TOWER);
    towerTypes.add(UnitType.LEVEL_TWO_DEFENSE_TOWER);
    towerTypes.add(UnitType.LEVEL_THREE_DEFENSE_TOWER);
    towerTypes.add(UnitType.LEVEL_ONE_MONEY_TOWER);
    towerTypes.add(UnitType.LEVEL_TWO_MONEY_TOWER);
    towerTypes.add(UnitType.LEVEL_THREE_MONEY_TOWER);

    for(RobotInfo robot : rc.senseNearbyRobots()){
      if(robot.getTeam() == rc.getTeam() && towerTypes.contains(robot.getType()))
        knownTowers.add(robot.getLocation());
    }
  }

}
