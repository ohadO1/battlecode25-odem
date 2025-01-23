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
    enemyDetected,
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
        rc.setIndicatorString("in roam");
        Utils.mopperRoam(rc);

        boolean didFindEnemyTower = false;

        for (RobotInfo robot : rc.senseNearbyRobots(4)) {
          if (robot.getType().isTowerType() && robot.getTeam() != rc.getTeam()) {
            didFindEnemyTower = true;
          }
        }

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
          if (rc.senseMapInfo(rc.getLocation()).getPaint().isAlly() && tileToAttack == null
              && tile.getPaint().isEnemy()) {
            if (!didFindEnemyTower) {
              safeRoamLoc = rc.getLocation();
              state = MOPPER_STATE.attackTile;
              if (tileToAttack == null) {
                tileToAttack = tile.getMapLocation();
              } else {
                tileToAttack = rc.getLocation().distanceSquaredTo(tile.getMapLocation()) < rc.getLocation()
                    .distanceSquaredTo(tileToAttack) ? tile.getMapLocation() : tileToAttack;
              }
            }
          }
        }
        if (tileToAttack != null) {
          break;
        }

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

        // transfer paint
        if (allyToRefill == null && state == MOPPER_STATE.roam) {
          for (RobotInfo robot : rc.senseNearbyRobots()) {
            if ((robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.SPLASHER
                || robot.getType() == UnitType.MOPPER) && robot.team == rc.getTeam()) {
              if (((double) robot.getPaintAmount()) / robot.getType().paintCapacity <= 0.3 || rc.getPaint() <= 30) {
                state = MOPPER_STATE.refillAlly;
                allyToRefill = robot;
                break;
              }
            }
          }
        }

        // if (ruinDest != null && knownTowersInfos.size() > 0) {
        // ruinDest = checkNearbyRuins(rc);
        // state = MOPPER_STATE.notifyTower;
        // towerDestination = Utils.findClosestTower(knownTowersInfos, rc);
        // }
        break;

      case refillAlly:
        rc.setIndicatorString("in refillAlly");
        int numToTransfer = rc.getPaint() > 60 ? 50 : rc.getPaint();
        for (RobotInfo robot : rc.senseNearbyRobots(5)) {
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
        if (rc.canTransferPaint(allyToRefill.location, numToTransfer)) {
          rc.transferPaint(allyToRefill.getLocation(), numToTransfer);
          allyToRefill = null;
          state = MOPPER_STATE.roam;
        } else {
          PathFinder.moveToLocation(rc, allyToRefill.getLocation());
        }
        break;

      case enemyDetected:
        mopperAttack(rc, rc.getLocation());
        state = MOPPER_STATE.roam;
        break;

      case attackTile:
        rc.setIndicatorString("in attack tile");
        // if a tower is too close to the tile stop trying to remove the paint
        Direction dir = rc.getLocation().directionTo(tileToAttack);
        for (RobotInfo robot : rc.senseNearbyRobots(4)) {
          if (rc.getLocation().add(dir).distanceSquaredTo(robot.getLocation()) < 3 && robot.getType().isTowerType()
              && robot.getTeam() != rc.getTeam()) {
            if (rc.canMove(dir.opposite())) {
              rc.move(dir.opposite());
            }
            tileToAttack = null;
            state = MOPPER_STATE.roam;
            break;
          }
        }
        if (!rc.canAttack(tileToAttack)) {
          rc.setIndicatorString("in path finder: " + tileToAttack);
          PathFinder.moveToLocation(rc, tileToAttack);
          break;
        } else {
          rc.setIndicatorString("in else");
          rc.attack(tileToAttack);
          for (MapInfo loc : rc.senseNearbyMapInfos(3)) {
            if (loc.getPaint().isEnemy()) {
              tileToAttack = loc.getMapLocation();
              break;
            }
          }
          tileToAttack = null;
          state = MOPPER_STATE.roam;
          break;
        }

      case goToTile:
        rc.setIndicatorString("in attack tile");
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
