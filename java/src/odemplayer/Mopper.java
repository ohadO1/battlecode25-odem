package odemplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.*;

public class Mopper extends Globals {

  private enum MOPPER_ROLES {
    messenger,
    refiller,
    normal,
    attack
  }

  private enum MOPPER_STATE {
    transferPaint,
    // im removing this for now (commenting out), moppers are inside the painted
    // territory anyway
    notifyTower,
    roam,
    attackEnemy,
    attackTile,
    refillAlly,
    defendTerritory,
    clearRuin,
    goToTile
  }

  static MOPPER_TASKS task = null;

  enum MOPPER_TASKS {
    transferPaint,
    attack,
    remove_enemy_paint
  }

  private static MOPPER_ROLES role = MOPPER_ROLES.normal;
  private static MapLocation tileToAttack = null;
  private static MapInfo ruinDest = null;
  private static MOPPER_STATE state = MOPPER_STATE.roam;
  private static MapLocation notifyDest;
  private static MapLocation safeRoamLoc;
  static MOPPER_STATE statePrev = state;
  static MapLocation goToTileDest;
  private static MapLocation towerDestination = null;
  static boolean stateChanged = false;
  static RobotInfo allyToRefill = null;
  static int refillWait = 0;
  static RobotInfo refillTower;
  static int notifyTowerCooldown = 500;
  static int goToTileTurns = 10;

  public static void runMopper(RobotController rc) throws GameActionException {
    notifyTowerCooldown--;
    Utils.updateFriendlyTowers(rc);
    // TODO: add a role that will support soldiers and refill them from tower
    // contantly
    // TODO: add check if task != null

    // mopswing enemies - move to state

    switch (state) {
      case roam:
        // remain only in our tiles
        rc.setIndicatorString("IN ROAM");

        Utils.mopperRoam(rc);

        // check for offending robots
        for (MapInfo location : rc.senseNearbyMapInfos()) {
          RobotInfo robotAtLocation = rc.senseRobotAtLocation(location.getMapLocation());
          if (robotAtLocation != null) {
            boolean isEnemy = robotAtLocation.getTeam() != rc.getTeam();
            boolean isOnOurTerritory = location.getPaint() == PaintType.ALLY_SECONDARY
                || location.getPaint() == PaintType.ALLY_PRIMARY;

            if (isEnemy && isOnOurTerritory && robotAtLocation.paintAmount >= 10) {
              state = MOPPER_STATE.defendTerritory;
            }
          }
        }
        // transfer paint
        if (allyToRefill == null) {
          for (RobotInfo robot : rc.senseNearbyRobots()) {
            if ((robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.SPLASHER
                || robot.getType() == UnitType.MOPPER) && robot.team == rc.getTeam()) {
              if ((((double) robot.getPaintAmount()) / robot.getType().paintCapacity <= 0.3) || rc.getPaint() <= 30) {
                state = MOPPER_STATE.refillAlly;
                allyToRefill = robot;
                break;
              }
            }
          }
        }
        if (state == MOPPER_STATE.refillAlly) {
          break;
        }

        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
          // find closest tile to attack, if found, change state to attackTile and set the
          // 'tileToAttack' variable
          if (currentTile.getPaint().isAlly()
              && tile.getPaint().isEnemy()) {
            state = MOPPER_STATE.attackTile;
            if (tileToAttack == null) {
              tileToAttack = tile.getMapLocation();
            } else {
              tileToAttack = rc.getLocation().distanceSquaredTo(tile.getMapLocation()) < rc.getLocation()
                  .distanceSquaredTo(tileToAttack) ? tile.getMapLocation() : tileToAttack;
            }
          }
        }
        // if tileToAttack were found, change the state, do not change furt
        if (state == MOPPER_STATE.attackTile) {
          break;
        }

        // TODO: messanger that scouts instead notifyTowerCooldown
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
          if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null && notifyTowerCooldown <= 0) {
            state = MOPPER_STATE.notifyTower;
            notifyDest = Utils.findClosestTower(knownTowersInfos, rc);
            ruinDest = tile;
            PathFinder.moveToLocation(rc, notifyDest);
            notifyTowerCooldown = 500;
            break;
          }
        }
        if (state == MOPPER_STATE.notifyTower) {
          break;
        }

        break;

      case refillAlly:
        rc.setIndicatorString("in refillAlly");
        int numToTransfer = rc.getPaint() > 60 ? 50 : rc.getPaint();
        // TODO: fix it, not good enough -
        for (RobotInfo robot : rc.senseNearbyRobots(3)) {
          if (robot.getType().isTowerType() && robot.getTeam() != rc.getTeam()) {
            allyToRefill = null;
            state = MOPPER_STATE.roam;
            Direction towerDirection = rc.getLocation().directionTo(robot.getLocation());
            if (rc.canMove(towerDirection.opposite())) {
              rc.move(towerDirection.opposite());
            }
            break;
          }
        }
        if (allyToRefill == null) {
          break;

        }
        if (rc.canTransferPaint(allyToRefill.location, numToTransfer)) {
          rc.transferPaint(allyToRefill.getLocation(), numToTransfer);
          allyToRefill = null;
          state = MOPPER_STATE.roam;
        } else {
          PathFinder.moveToLocation(rc, allyToRefill.getLocation());
        }
        break;

      case defendTerritory:
        rc.setIndicatorString("IN DEFEND");
        MapLocation closestRobotFound = null;
        int shortestDistance = 99999999;

        for (MapInfo location : rc.senseNearbyMapInfos()) {
          RobotInfo robotAtLocation = rc.senseRobotAtLocation(location.getMapLocation());
          if (robotAtLocation != null) {
            boolean isEnemy = robotAtLocation.getTeam() != rc.getTeam();
            boolean isOnOurTerritory = location.getPaint() == PaintType.ALLY_SECONDARY
                || location.getPaint() == PaintType.ALLY_PRIMARY;

            if (isEnemy && isOnOurTerritory && robotAtLocation.paintAmount >= 10) {
              int distance = rc.getLocation().distanceSquaredTo(robotAtLocation.getLocation());
              if (shortestDistance > distance) {
                shortestDistance = distance;
                closestRobotFound = robotAtLocation.getLocation();
              }
              if (rc.canAttack(location.getMapLocation())) {
                rc.attack(location.getMapLocation());
              }
            }
          }
        }
        if (closestRobotFound != null) {
          PathFinder.moveToLocation(rc, closestRobotFound);
        } else {
          state = MOPPER_STATE.roam;
        }

        break;

      case attackTile:
        rc.setIndicatorString("in attack tile");
        // if a tower is too close to the tile stop trying to remove the paint
        tileToAttack = Utils.findClosestEnemyTile(rc);

        if (tileToAttack == null) {
          state = MOPPER_STATE.roam;
          break;
        }

        Direction dir = rc.getLocation().directionTo(tileToAttack);
        for (RobotInfo robot : rc.senseNearbyRobots(4)) {

          MapLocation foundRobotLocation = robot.getLocation();
          MapLocation estimatedNewLocation = rc.getLocation().add(dir);
          if (estimatedNewLocation.distanceSquaredTo(foundRobotLocation) < 4 && robot.getType().isTowerType()
              && robot.getTeam() != rc.getTeam()) {
            if (rc.canMove(dir.opposite())) {
              rc.move(dir.opposite());
            }
            tileToAttack = null;
            state = MOPPER_STATE.roam;
            break;
          }
        }

        // if it can attack but theres a cooldown, wait for next turn
        if (rc.getActionCooldownTurns() > 0 && rc.getLocation().distanceSquaredTo(tileToAttack) <= 2) {
          rc.setIndicatorString("IN IF");
          break;
        }

        // if cant attack, progress to its direction,
        if (!rc.canAttack(tileToAttack)) {
          rc.setIndicatorString("in path finder: " + tileToAttack);
          PathFinder.moveToLocation(rc, tileToAttack);
          break;
        } else {
          rc.setIndicatorString("in else");
          // attack, if you can to continue attacking, continue
          for (MapInfo location : rc.senseNearbyMapInfos(2)) {
            RobotInfo robotAtLocation = rc.senseRobotAtLocation(location.getMapLocation());
            // check if theres a better tile to attack (one with a soldier on)
            if (robotAtLocation != null && robotAtLocation.getTeam() != rc.getTeam()
                && robotAtLocation.paintAmount >= 10
                && rc.canAttack(location.getMapLocation())) {
              rc.attack(location.getMapLocation());
            }
          }

          // fallback
          if (rc.canAttack(tileToAttack)) {
            rc.attack(tileToAttack);
          }
        }

      case goToTile:
        rc.setIndicatorString("in attack tile");

        MapInfo currTile = rc.senseMapInfo(rc.getLocation());
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
          // find closest tile to attack, if found, change state to attackTile and set the
          //
          if (currTile.getPaint().isAlly()
              && tile.getPaint().isEnemy()) {
            state = MOPPER_STATE.attackTile;
            if (tileToAttack == null) {
              tileToAttack = tile.getMapLocation();
            } else {
              tileToAttack = rc.getLocation().distanceSquaredTo(tile.getMapLocation()) < rc.getLocation()
                  .distanceSquaredTo(tileToAttack) ? tile.getMapLocation() : tileToAttack;
            }
          }
        }

        // if tileToAttack were found, change the state, do not change furt
        if (state == MOPPER_STATE.attackTile) {
          break;
        }
        if (goToTileTurns == 0 || !rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()) {
          state = MOPPER_STATE.roam;
          goToTileTurns = 13;
          goToTileDest = null;
          break;
        }
        rc.setIndicatorString("IN GO TO TILE" + goToTileDest);
        PathFinder.moveToLocation(rc, goToTileDest);
        goToTileTurns -= 1;
        break;

      case notifyTower:
        rc.setIndicatorString("in notifyTower");

        if (stateChanged)
          notifyDest = Utils.findClosestTower(knownTowersInfos, rc);

        // goto dest
        PathFinder.moveToLocation(rc, notifyDest);

        // reach dest, send message.
        if (rc.canSendMessage(notifyDest)) {
          rc.sendMessage(notifyDest, Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere, ruinDest.getMapLocation()));
          state = MOPPER_STATE.roam;
          notifyTowerCooldown = 500;
        }

        break;

      default:
        break;

    }

    Message[] messages = rc.readMessages(-1);
    for (Message msg : messages) {
      DecodedMessage<Object> message = new DecodedMessage<>(msg.getBytes());
      MESSAGE_TYPE type = message.type;
      switch (type) {
        case MESSAGE_TYPE.sendMopperToClearRuin:
          goToTileDest = (MapLocation) message.data;
          state = MOPPER_STATE.goToTile;
          break;
        case MESSAGE_TYPE.sendMopperToCenterOfMap:
          if (goToTileDest == null) {
            goToTileDest = (MapLocation) message.data;
            state = MOPPER_STATE.goToTile;
          }
          break;
      }

    }
  }

  // TODO: change it
  public static void mopperAttack(RobotController rc, MapLocation nextLoc) throws GameActionException {
    for (Direction tryMopDirection : directions) {
      if (rc.canMopSwing(tryMopDirection)) {
        rc.mopSwing(tryMopDirection);
      }
    }
    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }

  }

  public static MapLocation checkNearbyRuins(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

    for (MapInfo tile : nearbyTiles) {

      if (!tile.hasRuin())
        continue;

      if (rc.senseRobotAtLocation(tile.getMapLocation()) != null) {
        continue;
      }

      Direction dir = tile.getMapLocation().directionTo(rc.getLocation());
      MapLocation markTile = tile.getMapLocation().add(dir);

      if (!rc.senseMapInfo(markTile).getMark().isAlly()) {
        continue;
      }

      // found not occupied ruin
      return tile.getMapLocation();
    }
    return null;
  }

  public static void determineMopperRole(RobotController rc) {
    int id = rc.getID();
    switch (id % 2) {
      case 0:
        Mopper.role = MOPPER_ROLES.messenger;
      default:
        Mopper.role = MOPPER_ROLES.normal;
    }
  }

  /**
   * @param - how much paint to take or give to the unit (positive to give,
   *          negative to take)
   * @param - targetLocation
   * @return void
   */
  public static void tranferPaintToLocation(RobotController rc,
      MapLocation targetLocation, int amount) throws GameActionException {

    if (rc.canTransferPaint(targetLocation, amount)) {
      rc.transferPaint(targetLocation, amount);
      return;
    }

    PathFinder.moveToLocation(rc, targetLocation);
  }

}
