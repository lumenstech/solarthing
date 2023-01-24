package me.retrodaredevil.action.node.expression.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import me.retrodaredevil.action.node.environment.ActionEnvironment;
import me.retrodaredevil.action.node.expression.BooleanExpression;
import me.retrodaredevil.action.node.expression.ComparisonExpression;

import static java.util.Objects.requireNonNull;

@JsonTypeName("comparison")
public class ComparisonExpressionNode implements ExpressionNode {
	private final ExpressionNode lhs;
	private final ExpressionNode rhs;
	private final ComparisonExpression.Operator operator;

	@JsonCreator
	public ComparisonExpressionNode(
			@JsonProperty(value = "lhs", required = true) ExpressionNode lhs,
			@JsonProperty(value = "rhs", required = true) ExpressionNode rhs,
			@JsonProperty(value = "operator", required = true) ComparisonExpression.Operator operator) {
		requireNonNull(this.lhs = lhs);
		requireNonNull(this.rhs = rhs);
		requireNonNull(this.operator = operator);
	}

	@Override
	public BooleanExpression createExpression(ActionEnvironment actionEnvironment) {
		return new ComparisonExpression(
				lhs.createExpression(actionEnvironment),
				rhs.createExpression(actionEnvironment),
				operator
		);
	}
}
