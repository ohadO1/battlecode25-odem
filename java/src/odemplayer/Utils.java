package odemplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Random;

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
   * moves using pathfinder in a circular manner, the radius increasing with time.
   * the redius resets on its own if not run for CIRCLE_ROAM_ROUNDS_TO_RESET
   * rounds.
   * the destination updates when you reach it
   *
   * @param rc - RobotController
   * @return destination - MapLocation
   */
  public static MapLocation roamCircle(RobotController rc) throws GameActionException {

    // reset if havent used this for a while
    if (rc.getRoundNum() - circleRoamUpdate > CIRCLE_ROAM_ROUNDS_TO_RESET && false) {

      circleRoamRadius = 1;
      circleRoamDest = rc.getLocation();
      circleRoamCenter = circleRoamDest;
    }
    circleRoamUpdate = rc.getRoundNum();

    // set new dest
    while (circleRoamDest == null || rc.getLocation().distanceSquaredTo(circleRoamDest) < 2
        || !withinBounds(rc, circleRoamDest) || idleTime >= MAX_IDLE) {

      int quaterPrev = circleRoamAngle / 90;

      // progress angle
      int aAdd = (30) / (circleRoamRadius + 1) + 10;
      circleRoamAngle = (circleRoamAngle + aAdd) % 360;

      // increase radius
      if (circleRoamAngle / 90 != quaterPrev)
        circleRoamRadius = (circleRoamRadius + 1) % CIRCLE_ROAM_MAX_RADIUS;

      // find dest
      double x = circleRoamCenter.x + circleRoamRadius * Math.cos(Math.toRadians(circleRoamAngle));
      double y = circleRoamCenter.y + circleRoamRadius * Math.sin(Math.toRadians(circleRoamAngle));
      x = Math.clamp((int) x, 0, rc.getMapWidth() - 1);
      y = Math.clamp((int) y, 0, rc.getMapWidth() - 1);
      circleRoamDest = new MapLocation((int) x, (int) y);
      // System.out.println("-- circle: chose " + circleRoamDest + ", center: " +
      // circleRoamCenter + ", r: " + circleRoamRadius + ", a: " + circleRoamAngle);

      // cancel if i know its unreachable
      if (rc.canSenseLocation(circleRoamDest)) {
        if (rc.senseRobotAtLocation(circleRoamDest) != null || rc.senseMapInfo(circleRoamDest).isWall())
          circleRoamDest = null;
      }
    }

    // approach dest
    PathFinder.moveToLocation(rc, circleRoamDest);

    rc.setIndicatorDot(circleRoamDest, 204, 0, 204);

    return circleRoamDest;
  }

  /**
   * picks a random location within the center of the map and aims to go there
   *
   * @param rc - RobotController
   * @return destination - MapLocation
   */
  public static MapLocation roamDest(RobotController rc) throws GameActionException {
    if (roamDestDest == null || rc.getLocation().distanceSquaredTo(roamDestDest) < 3
        || (rc.canSenseLocation(roamDestDest) && rc.isLocationOccupied(roamDestDest))) {
      int w = rc.getMapWidth();
      int h = rc.getMapHeight();
      int x = (int) (0.3 * w + Math.random() * 0.4 * w);
      int y = (int) (0.3 * h + Math.random() * 0.4 * h);
      roamDestDest = new MapLocation(x, y);
    }

    PathFinder.moveToLocation(rc, roamDestDest);

    return roamDestDest;
  }

  public static boolean withinBounds(RobotController rc, MapLocation pos) {
    if (pos.x < 0)
      return false;
    if (pos.y < 0)
      return false;
    if (pos.x >= rc.getMapWidth())
      return false;
    return pos.y < rc.getMapHeight();
  }

  /**
   * finds the closest tower (out of the known tower locations)
   * 
   * @param rc              - RobotController
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

  // should it really return the position and not the robot? why?
  /**
   * finds the closest tower (out of the known tower locations)
   *
   * @param rc              - RobotController
   * @param knownTowersInfo - ArrayList<MapLocation>
   * @param typeFilter      - UnitType[]
   * @return closestTowerLocation - MapLocation
   */
  public static MapLocation findClosestTower(ArrayList<RobotInfo> knownTowersInfo, RobotController rc,
      UnitType[] typeFilter) {
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

  public static RobotInfo findClosestTowerInfo(ArrayList<RobotInfo> knownTowersInfo, RobotController rc) {
    int distance = 99999;
    RobotInfo ret = null;

    for (RobotInfo knownTower : knownTowersInfos) {
      MapLocation location = knownTower.getLocation();
      int foundDistance = location.distanceSquaredTo(rc.getLocation());
      if (distance > foundDistance) {
        distance = foundDistance;
        ret = knownTower;
      }
    }
    if (ret == null)
      System.out.println("asked to find closest tower and returned null.");
    return ret;
  }

  public static MapLocation mopperRoam(RobotController rc) throws GameActionException {

    MapLocation closestTower = findClosestTower(knownTowersInfos, rc);
    boolean isNotAllyTerritory = !rc.senseMapInfo(rc.getLocation()).getPaint().isAlly();
    boolean isFarFromTower = closestTower.isWithinDistanceSquared(rc.getLocation(), 4);

    if (isNotAllyTerritory) {
      PathFinder.moveToLocation(rc, closestTower);
      return null;
    }

    List<Direction> dirs = Arrays.asList(directions);
    Collections.shuffle(dirs);
    for (Direction dir : directions) {
      MapLocation newLocation = rc.getLocation().add(dir);
      if (rc.canSenseLocation(newLocation) && rc.senseMapInfo(newLocation).getPaint().isAlly() && rc.canMove(dir)) {
        boolean canGoInDir = true;
        for (RobotInfo robot : rc.senseNearbyRobots(4)) {
          if (robot.getLocation().distanceSquaredTo(newLocation) < 3 && robot.getType().isTowerType()
              && robot.getTeam() != rc.getTeam()) {
            canGoInDir = false;
          }
        }
        if (canGoInDir) {
          rc.move(dir);
          return rc.getLocation().add(dir);
        }
      }
    }
    for (MapInfo maploc : rc.senseNearbyMapInfos(4)) {
      Direction dir = rc.getLocation().directionTo(maploc.getMapLocation());
      if (rc.canMove(dir)) {
        rc.move(dir);
      }
    }

    return null;
  }

  /****************** decision making functions ****************************/

  public static UnitType WhatShouldIBuild(RobotController rc, MapLocation location, GAME_PHASE phase) {

    // dont build defense in early game
    if (phase == GAME_PHASE.early)
      for (UnitType unitType : idealTowerOrder) {
        if (unitType != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
          return unitType;
        }
      }

    // build whatever's in queue
    if (!idealTowerOrder.isEmpty())
      return idealTowerOrder.getFirst();

    // queue is over (dafuq?) roll.
    return Arrays
        .asList(UnitType.LEVEL_ONE_DEFENSE_TOWER, UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_PAINT_TOWER)
        .get(rng.nextInt(3));

    // UnitType choice = DEFUALT_TOWER_TO_BUILD;
    // if(rc.canBuildRobot(choice,location))
    // return choice;
    //
    // return choice != null ? choice : DEFUALT_TOWER_TO_BUILD;
  }

  public static boolean ShouldIBuild(RobotController rc, ArrayList<RobotInfo> knownTowers) {
    // if i have enough paint and tower is far do it

    return true;
  }

  public static boolean shouldIAttackTower(RobotController rc, RobotInfo tower, GAME_PHASE gamePhase)
      throws GameActionException {

    // if tower is in critical hp just go get it
    if (tower.getHealth() <= TOWER_CRITICAL_HP)
      return true;

    // find friends
    RobotInfo[] friends = rc.senseNearbyRobots(tower.getLocation(), -1, rc.getTeam());
    int count = 1; // im my own best friend
    for (RobotInfo robot : friends) {
      if (robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.SPLASHER)
        count++;
    }

    switch (gamePhase) {
      case GAME_PHASE.early:
        return count >= ALLIES_FOR_ATTACK_EARLY;
      case GAME_PHASE.mid:
        return count >= ALLIES_FOR_ATTACK_MID;
      case GAME_PHASE.late:
        return count >= ALLIES_FOR_ATTACK_LATE;
    }

    return false;
  }

  /*
   * =========== message encoding ==========
   * there are a bunch of overloading, each may contain a few message types if
   * they require the same arguments.
   * the smallest digit is used for message type.
   * in general, information is read from smallest to largest bit, so you could
   * do:
   * info = msg%10; msg /= 10;
   */

  public static int encodeMessage(MESSAGE_TYPE type, MapLocation location) { // ask for refill
    int ret = type.ordinal();

    switch (type) {
      case MESSAGE_TYPE.buildTowerHere:
      case MESSAGE_TYPE.askForRefill:
      case MESSAGE_TYPE.sendMopperToCenterOfMap:
      case MESSAGE_TYPE.sendMopperToClearRuin:
        int x = location.x, y = location.y;
        ret += x * 10 + y * 1000;
        break;
    }

    // System.out.println("location message encoded: " + ret);
    // DecodedMessage msg = new DecodedMessage(ret);

    return ret;
  }

  public static int encodeMessage(MESSAGE_TYPE type, int amount) { // ask for refill
    int ret = type.ordinal();

    switch (type) {
      case MESSAGE_TYPE.saveChips:
        ret += amount * 10;
        break;
    }

    // System.out.println("int message encoded: " + ret);

    return ret;
  }

  // TODO: send encoded message and parse
  public static boolean updateFriendlyTowers(RobotController rc) throws GameActionException {
    // Search for all nearby robots
    RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
    for (RobotInfo ally : allyRobots) {

      // feel free to uncomment this if you have any idea what's its purpose.
      // MapLocation allyLocation = ally.location;
      //
      // RobotInfo knownTowersAllyLocation = knownTowersInfos.stream()
      // .filter(tower -> tower.location == ally.location).findFirst().orElse(null);

      if (!knownTowersInfos.contains(ally) && ally.getType().isTowerType())
        knownTowersInfos.add(ally);
      updateTowerPriority(ally.getType());
    }

    return false;
  }

  public static void updateTowerPriority(UnitType type) throws GameActionException {
    if (type == UnitType.LEVEL_TWO_PAINT_TOWER || type == UnitType.LEVEL_THREE_PAINT_TOWER)
      type = UnitType.LEVEL_ONE_PAINT_TOWER;
    if (type == UnitType.LEVEL_TWO_MONEY_TOWER || type == UnitType.LEVEL_THREE_MONEY_TOWER)
      type = UnitType.LEVEL_ONE_MONEY_TOWER;
    if (type == UnitType.LEVEL_TWO_DEFENSE_TOWER || type == UnitType.LEVEL_THREE_DEFENSE_TOWER)
      type = UnitType.LEVEL_ONE_DEFENSE_TOWER;

    for (int i = 0; i < idealTowerOrder.size(); i++) {
      if (idealTowerOrder.get(i) == type)
        idealTowerOrder.remove(i);
      return;
    }
  }

  public static Direction getRandomDirection(RobotController rc) throws GameActionException {
    Direction[] directions = Direction.allDirections();
    Direction randomDirection;
    randomDirection = directions[(int) (Math.random() * directions.length)];
    if (rc.canMove(randomDirection))
      return randomDirection;
    else
      return getRandomDirection(rc);
  }
}
