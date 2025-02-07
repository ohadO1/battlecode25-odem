package odemplayer_tournament1;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

public class Globals {

  public enum MessageType {
    SAVE_CHIPS
  }

  // TODO: some of the globals should be more specific (tower or unit scope)
  // TODO: get width and height of the map for further calculations
  //
  static int turnCount = 0;
  public enum GAME_PHASAE{
    early,
    mid,
    late,
  }

  // messanger:
  static boolean isMessanger = false;

  static ArrayList<MapLocation> knownTowers = new ArrayList<>();

  static boolean isSaving = false;
  //

  // tower
  static int savingTurns = 0;
  //

  static final Random rng = new Random();

  static final Direction[] directions = {
      Direction.NORTH,
      Direction.NORTHEAST,
      Direction.EAST,
      Direction.SOUTHEAST,
      Direction.SOUTH,
      Direction.SOUTHWEST,
      Direction.WEST,
      Direction.NORTHWEST,
  };
}
