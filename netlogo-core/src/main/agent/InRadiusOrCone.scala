// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.agent

import org.nlogo.api.AgentException
import org.nlogo.core.AgentKind
import java.util.{ArrayList, HashSet => JHashSet, List => JList}

class InRadiusOrCone private[agent](val world: World2D) extends World.InRadiusOrCone {
//  private val patches = null // TODO ? cache patches

  override def inRadiusSimple(agent: Agent, sourceSet: AgentSet, radius: Double, wrap: Boolean) = {
    InRadiusSimple.apply(world)(agent, sourceSet, radius, wrap)
  }

  override def inRadius(agent: Agent, sourceSet: AgentSet, radius: Double, wrap: Boolean): JList[Agent] = {
    val worldWidth = world.worldWidth
    val worldHeight = world.worldHeight

    val result = new ArrayList[Agent]
    var startPatch: Patch = null
    var startX: Double = .0
    var startY: Double = .0
    var gRoot: Double = .0
    var dx: Int = 0
    var dy: Int = 0

    // set agent coordinates
    if (agent.isInstanceOf[Turtle]) {
      val startTurtle = agent.asInstanceOf[Turtle]
      startX = startTurtle.xcor
      startY = startTurtle.ycor
    }
    else {
      startPatch = agent.asInstanceOf[Patch]
      startX = startPatch.pxcor
      startY = startPatch.pycor
    }

    val cachedIDs = initCachedIDs(sourceSet)
    val patches = getPatches(startX, startY, radius)

    var i = 0

    if (sourceSet.kind eq AgentKind.Patch) {
      val sourceSetIsWorldPatches = sourceSet eq world.patches

      while (i < patches.length) {
        val patch = patches(i)

        if (world.protractor.distance(patch.pxcor, patch.pycor, startX, startY, wrap) <= radius
          && (sourceSetIsWorldPatches || cachedIDs.contains(patch.id))) {
          result.add(patch)
        }

        i += 1
      }
    } else if (sourceSet.kind eq AgentKind.Turtle) {
      val sourceSetIsWorldTurtles = sourceSet eq world.turtles
      val sourceSetIsBreedSet = sourceSet.isBreedSet

      while (i < patches.length) {
        val patch = patches(i)

        dx = Math.abs(patch.pxcor - startX.toInt)
        if (dx > worldWidth / 2)
          dx = worldWidth - dx

        dy = Math.abs(patch.pycor - startY.toInt)
        if (dy > worldHeight / 2)
          dy = worldHeight - dy

        gRoot = world.rootsTable.gridRoot(dx * dx + dy * dy)

        // The 1.415 (square root of 2) adjustment is necessary because it is
        // possible for portions of a patch to be within the circle even though
        // the center of the patch is outside the circle.  Both turtles, the
        // turtle in the center and the turtle in the agentset, can be as much
        // as half the square root of 2 away from its patch center.  If they're
        // away from the patch centers in opposite directions, that makes a total
        // of square root of 2 additional distance we need to take into account.
        if (gRoot <= radius + 1.415) {
          patch.turtlesHere.forEach({ turtle =>
            if ((sourceSetIsWorldTurtles
              || (sourceSetIsBreedSet && (sourceSet eq turtle.getBreed))
              || cachedIDs.contains(turtle.id))
              && (gRoot <= radius - 1.415
              || world.protractor.distance(turtle.xcor, turtle.ycor, startX, startY, wrap) <= radius))
              result.add(turtle)
          })
        }
        i += 1
      }
    }

    result
  }

  override def inCone(startTurtle: Turtle, sourceSet: AgentSet, radius: Double, angle: Double, wrap: Boolean): JList[Agent] = {
    val worldWidth = world.worldWidth
    val worldHeight = world.worldHeight

    // val?
    var m = 0
    var n = 0
    // If wrap is true and the radius is large enough, the cone
    // may wrap around the edges of the world.  We handle this by
    // enlarging the coordinate system in which we search beyond
    // the world edges and then filling the enlarged coordinate
    // system with "copies" of the world.  At least, you can
    // imagine it that way; we don't actually copy anything.  m
    // and n are the maximum number of times the cone might wrap
    // around the edge of the world in the X and Y directions, so
    // that's how many world copies we will need to make.  The
    // copies will range from -m to +m on the x axis and -n to +n
    // on the y axis.
    if (wrap) {
      if (world.wrappingAllowedInX)
        m = StrictMath.ceil(radius / worldWidth).toInt

      if (world.wrappingAllowedInY)
        n = StrictMath.ceil(radius / worldHeight).toInt
    }
    // in the nonwrapping case, we don't need any world copies besides
    // the original, so we have only one pair of offsets and both of
    // them are 0

    val result = new ArrayList[Agent]
    val half: Double = angle / 2

    var gRoot: Double = .0
    var dx: Int = 0
    var dy: Int = 0

    val cachedIDs = initCachedIDs(sourceSet)
    val patches = getPatches(startTurtle.xcor, startTurtle.ycor, radius)

    var i = 0

    if (sourceSet.kind eq AgentKind.Patch) {
      val sourceSetIsWorldPatches = sourceSet eq world.patches

      while (i < patches.length) {
        val patch = patches(i)

        if (patch != null) {
          // loop through the patches in the rectangle.  (it doesn't matter what
          // order we check them in.)
          var worldOffsetX = -m // TODO ? start iteration at 0 instead
          while (worldOffsetX <= m) { // TODO ? macro for repeated loops
            var worldOffsetY = -n
            while (worldOffsetY <= n) {
              if ((sourceSetIsWorldPatches || cachedIDs.contains(patch.id))
                && isInCone(patch.pxcor + worldWidth * worldOffsetX, patch.pycor + worldHeight * worldOffsetY, startTurtle.xcor, startTurtle.ycor, radius, half, startTurtle.heading)) {
                result.add(patch)

                // break out of while loops: ? better way to do this?
                worldOffsetX = m
                worldOffsetY = n
              }

              worldOffsetY += 1
            }

            worldOffsetX += 1
          }
        }
        i += 1
      }
    } else {
      val sourceSetIsWorldTurtles = sourceSet eq world.turtles
      val sourceSetIsBreedSet = sourceSet.isBreedSet

      while (i < patches.length) {
        val patch = patches(i)

        dx = Math.abs(patch.pxcor - startTurtle.xcor.toInt)
        if (dx > worldWidth / 2)
          dx = worldWidth - dx

        dy = Math.abs(patch.pycor - startTurtle.ycor.toInt)
        if (dy > worldHeight / 2)
          dy = worldHeight - dy

        gRoot = world.rootsTable.gridRoot(dx * dx + dy * dy)

        // Only check patches that might have turtles within the radius on them.
        // The 1.415 (square root of 2) adjustment is necessary because it is
        // possible for portions of a patch to be within the circle even though
        // the center of the patch is outside the circle.  Both turtles, the
        // turtle in the center and the turtle in the agentset, can be as much
        // as half the square root of 2 away from its patch center.  If they're
        // away from the patch centers in opposite directions, that makes a total
        // of square root of 2 additional distance we need to take into account.
        if (gRoot <= radius + 1.415) {
          patch.turtlesHere().forEach({ turtle =>

            var worldOffsetX = -m
            while (worldOffsetX <= m) {
              var worldOffsetY = -n
              while (worldOffsetY <= n) { // any turtle set with a non-null print name is either
                // the set of all turtles, or a breed agentset - ST 2/19/04
                if ((sourceSetIsWorldTurtles || (sourceSetIsBreedSet && (sourceSet eq turtle.getBreed))
                  || cachedIDs.contains(turtle.id))
                  && isInCone(turtle.xcor + worldWidth * worldOffsetX, turtle.ycor + worldHeight * worldOffsetY, startTurtle.xcor, startTurtle.ycor, radius, half, startTurtle.heading)) {
                  result.add(turtle)

                  // break out of while loops:
                  worldOffsetX = m
                  worldOffsetY = n
                }
                worldOffsetY += 1
              }
              worldOffsetX += 1
            }
          })
        }
        i += 1
      }
    }

    result
  }

  // helper method for inCone().
  // check if (x, y) is in the cone with center (cx, cy) , radius r, half-angle half, and central
  // line of the cone having heading h.
  private def isInCone(x: Double, y: Double, cx: Double, cy: Double, r: Double, half: Double, h: Double): Boolean = {
    if (x == cx && y == cy) {
      return true
    }
    if (world.protractor.distance(cx, cy, x, y, false) > r) { // false = don't wrap, since inCone()
      // handles wrapping its own way
      return false
    }
    var theta = .0
    try {
      theta = world.protractor.towards(cx, cy, x, y, false)
    } catch {
      case e: AgentException =>
        // this should never happen because towards() only throws an AgentException
        // when the distance is 0, but we already ruled out that case above
        throw new IllegalStateException(e.toString)
    }
    val diff = StrictMath.abs(theta - h)
    // we have to be careful here because e.g. the difference between 5 and 355
    // is 10 not 350... hence the 360 thing
    (diff <= half) || ((360 - diff) <= half)
  }

  // helper method to copy relevant patches
  private def getPatches(x: Double, y: Double, r: Double): Array[Patch] = {
    val regions = world.topology.getRegion(x,y,r) 

    var count = 0
    regions.forEach(region => count += region._2 - region._1)

    val patches = new Array[Patch](count)

    val worldPatches = world.patches.asInstanceOf[ArrayAgentSet].array
    var curr = 0
    var length = 0
    var r1 = 0
    var r2 = 0

    regions.forEach({ region => // TODO ? how fast is foreach on ArrayList?
      r1 = region._1
      r2 = region._2
      length = r2 - r1
      System.arraycopy(worldPatches, r1, patches, curr, length)
      curr += length
    })

    patches
  }

  // helper method to create cachedIDs set
  private def initCachedIDs(sourceSet: AgentSet): JHashSet[Long] = {
    var cachedIDs: JHashSet[Long] = null
    if (!sourceSet.isBreedSet) {
      cachedIDs = new JHashSet[Long](sourceSet.count)
      val sourceTurtles = sourceSet.iterator
      while (sourceTurtles.hasNext) {
        val t = sourceTurtles.next()
        cachedIDs.add(t.id)
      }
    } else {
      cachedIDs = new JHashSet[Long](0)
    }

    cachedIDs
  }
}
