package odemplayer;

import java.util.ArrayList;
import java.util.Arrays;

import battlecode.common.*;

public class Utils extends Globals {

  // TODO: not efficient enough! fix
  // TODO: add actual logic in WhatShouldIbuild, important function.
  // TODO: finish ShouldIBuild
  /**
   * method that avoids walls when roaming (no task yet)
   * 
   * @param rc - RobotController
   * @return next location - MapLocation
   */
  public static MapLocation roamGracefullyf(RobotController rc) throws GameActionException {

    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(2);
    for (MapInfo tile : nearbyTiles) {
      if (tile.isWall()) {
        MapLocation wallLocation = tile.getMapLocation();
        Direction dir = rc.getLocation().directionTo(wallLocation).opposite();
        if (rc.canMove(dir)) {

          rc.setIndicatorDot(rc.getLocation(), 0, 0, 255);
          rc.move(dir);
          return rc.getLocation().add(dir);
        }
        return null;
      }
    }

    Direction dir = directions[rng.nextInt(directions.length)];

    if (rc.canMove(dir)) {
      rc.move(dir);
    }

    return rc.getLocation().add(dir);
  }

  /**
   * finds the closest tower (out of the known tower locations)
   * 
   * @param rc                   - RobotController
   * @param knownTowersInfo - ArrayList<MapLocation>
   * @return closestTowerLocation - MapLocation
   */
  public static MapLocation findClosestTower(ArrayList<RobotInfo> knownTowersInfo, RobotController rc) {
    int distance = 99999;
    MapLocation closestLocation = null;

    for (RobotInfo knownTower : knownTowersInfos) {
      MapLocation location = knownTower.getLocation();
      int foundDistance = location.distanceSquaredTo(rc.getLocation());
      if (distance > foundDistance) {
        distance = foundDistance;
        closestLocation = location;
        continue;
      }
    }
    return closestLocation;
  }
  //should it really return the position and not the robot? why?
  /**
   * finds the closest tower (out of the known tower locations)
   *
   * @param rc                   - RobotController
   * @param knownTowersInfo - ArrayList<MapLocation>
   * @param typeFilter - UnitType[]
   * @return closestTowerLocation - MapLocation
   */
  public static MapLocation findClosestTower(ArrayList<RobotInfo> knownTowersInfo, RobotController rc, UnitType[] typeFilter) {
    int distance = 99999;
    MapLocation closestLocation = null;

    for (RobotInfo knownTower : knownTowersInfos) {
      MapLocation location = knownTower.getLocation();
      int foundDistance = location.distanceSquaredTo(rc.getLocation());
      if (distance > foundDistance && Arrays.asList(typeFilter).contains(knownTower.getType())) {
        distance = foundDistance;
        closestLocation = location;
        continue;
      }
    }
    return closestLocation;
  }

  //decision making functions
  public static UnitType WhatShouldIBuild(RobotController rc, MapLocation location){

    UnitType choice = DEFUALT_TOWER_TO_BUILD;
    if(rc.canBuildRobot(choice,location))
      return choice;

    return choice != null ? choice : DEFUALT_TOWER_TO_BUILD;
  }
  public static boolean ShouldIBuild(RobotController rc, ArrayList<RobotInfo> knownTowers){
    //if i have enough paint and tower is far do it

    return true;
  }

  /*
    =========== message encoding ==========
    there are a bunch of overloading, each may contain a few message types if they require the same arguments.
    the smallest digit is used for message type.
    in general, information is read from smallest to largest bit, so you could do:
    info = msg%10; msg /= 10;
  */

  public static int encodeMessage(MESSAGE_TYPE type, MapLocation location){  //ask for refill
    int ret = type.ordinal();

    switch(type){
      case MESSAGE_TYPE.buildTowerHere:
      case MESSAGE_TYPE.askForRefill:
        int x = location.x, y = location.y;
        ret += x*10 + y*1000;
      break;
    }

//    System.out.println("message encoded: " + ret);
    DecodedMessage msg = new DecodedMessage(ret);

    return ret;
  }
  public static int encodeMessage(MESSAGE_TYPE type, int amount){  //ask for refill
    int ret = type.ordinal();

    switch(type){
      case MESSAGE_TYPE.saveChips:
        ret += amount*10;
        break;
    }

    System.out.println("message encoded: " + ret);

    return ret;
  }



  // TODO: send encoded message and parse
  public static boolean updateFriendlyTowers(RobotController rc) throws GameActionException {
    // Search for all nearby robots
    RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
    for (RobotInfo ally : allyRobots) {
      if (!ally.getType().isTowerType())
        continue;

      MapLocation allyLocation = ally.location;

      RobotInfo knownTowersAllyLocation = knownTowersInfos.stream()
          .filter(tower -> tower.location == ally.location).findFirst().orElse(null);

      if (knownTowersAllyLocation != null) {
        if (rc.canSendMessage(allyLocation)) {
          rc.sendMessage(allyLocation, MESSAGE_TYPE.saveChips.ordinal());
          return true;
        }
        continue;
      }

      knownTowersInfos.add(ally);
    }

    return false;
  }

}
