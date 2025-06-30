package com.alibaba.cloud.ai.review.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审核决策分发器
 * 根据人工审核员的决策，决定下一步流程
 * 
 * @author Jast
 */
public class ReviewDecisionDispatcher implements EdgeAction {

    private static final Logger logger = LoggerFactory.getLogger(ReviewDecisionDispatcher.class);

    @Override
    public String apply(OverAllState state) throws Exception {
        String reviewAction = (String) state.value("review_action", "approve");
        String nextNode = (String) state.value("human_next_node", StateGraph.END);
        
        logger.info("ReviewDecisionDispatcher: review_action={}, human_next_node={}", reviewAction, nextNode);
        
        // 如果人工审核节点已经明确指定了下一个节点，优先使用
        if (!StateGraph.END.equals(nextNode)) {
            return nextNode;
        }
        
        // 否则根据审核动作决定
        switch (reviewAction.toLowerCase()) {
            case "approve":
            case "通过":
                logger.info("Document approved, proceeding to approval process");
                return "approval_process";
                
            case "reject":
            case "拒绝":
                logger.info("Document rejected, proceeding to rejection process");
                return "rejection_process";
                
            case "modify":
            case "修改":
                logger.info("Document needs modification, proceeding to modification process");
                return "modification_process";
                
            default:
                logger.warn("Unknown review action: {}, ending workflow", reviewAction);
                return StateGraph.END;
        }
    }
}
