// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim

import org.nlogo.agent.{ Agent, AgentSet }
import org.nlogo.api.{ AgentException, LogoListBuilder }
import org.nlogo.core.{ I18N, LogoList, Syntax }
import org.nlogo.nvm.{ ArgumentTypeException, Context, Reporter }
import org.nlogo.nvm.RuntimePrimitiveException

class _turtleorlinkvariableof(_varName: String) extends Reporter {

  override def toString =
    super.toString + ":" + varName

  // MethodRipper won't let us call a public method from report_1()
  // so we must keep varName and _varName separate - ST 9/22/12
  def varName = _varName

  override def report(context: Context): AnyRef =
    report_1(context, args(0).report(context))

  def report_1(context: Context, agentOrSet: AnyRef): AnyRef =
    try agentOrSet match {
      case agent: Agent =>
        if (agent.id == -1)
          throw new RuntimePrimitiveException(context, this,
            I18N.errors.getN("org.nlogo.$common.thatAgentIsDead",
                             agent.classDisplayName))
        agent.getTurtleOrLinkVariable(_varName)
      case sourceSet: AgentSet =>
        val result = new LogoListBuilder
        val iter = sourceSet.shufflerator(context.job.random)
        while(iter.hasNext)
          result.add(iter.next().getTurtleOrLinkVariable(_varName))
        result.toLogoList
     case _ =>
      throw new ArgumentTypeException(
        context, this, 0,
        Syntax.TurtlesetType | Syntax.TurtleType,
        agentOrSet)
    }
    catch { case ex: AgentException =>
      throw new RuntimePrimitiveException(context, this, ex.getMessage) }

  def report_2(context: Context, agent: Agent): AnyRef =
    try {
      if (agent.id == -1)
        throw new RuntimePrimitiveException(context, this,
          I18N.errors.getN("org.nlogo.$common.thatAgentIsDead",
                           agent.classDisplayName))
      agent.getTurtleOrLinkVariable(_varName)
    }
    catch { case ex: AgentException =>
      throw new RuntimePrimitiveException(context, this, ex.getMessage) }

  def report_3(context: Context, sourceSet: AgentSet): LogoList =
    try {
      val result = new LogoListBuilder
      val iter = sourceSet.shufflerator(context.job.random)
      while(iter.hasNext)
        result.add(iter.next().getTurtleOrLinkVariable(_varName))
      result.toLogoList
    }
    catch { case ex: AgentException =>
      throw new RuntimePrimitiveException(context, this, ex.getMessage) }

}
