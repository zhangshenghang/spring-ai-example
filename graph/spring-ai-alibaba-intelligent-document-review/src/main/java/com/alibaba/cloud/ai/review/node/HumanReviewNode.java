package com.alibaba.cloud.ai.review.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 人工审核节点 - 核心的人类返回节点
 * 等待人类审核员对AI分析结果进行确认和决策
 * 这是演示人类返回节点特性的关键节点
 * 
 * @author Jast
 */
public class HumanReviewNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(HumanReviewNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Human review node is running - waiting for human feedback...");
        
        HashMap<String, Object> resultMap = new HashMap<>();
        
        // 获取AI分析的结果
        String aiAnalysisResult = state.value("ai_analysis_result", "");
        int riskScore = (Integer) state.value("risk_score", 5);
        String documentType = state.value("document_type", "general");
        
        logger.info("Preparing for human review - Document type: {}, Risk score: {}", documentType, riskScore);
        
        // 检查是否已有人类反馈
        Map<String, Object> feedBackData = state.humanFeedback().data();
        if (feedBackData != null && !feedBackData.isEmpty()) {
            // 处理人类反馈
            String reviewAction = (String) feedBackData.getOrDefault("review_action", "approve");
            String reviewerComments = (String) feedBackData.getOrDefault("reviewer_comments", "");
            String suggestedChanges = (String) feedBackData.getOrDefault("suggested_changes", "");
            
            logger.info("Processing human feedback - Action: {}, Comments: {}", reviewAction, reviewerComments);
            
            // 根据人类反馈决定下一步
            String nextStep = determineNextStep(reviewAction, riskScore);
            
            resultMap.put("review_action", reviewAction);
            resultMap.put("reviewer_comments", reviewerComments);
            resultMap.put("suggested_changes", suggestedChanges);
            resultMap.put("human_next_node", nextStep);
            resultMap.put("human_review_timestamp", System.currentTimeMillis());
            
            logger.info("Human review completed - Next step: {}", nextStep);
        } else {
            // 首次进入人工审核节点，准备审核信息
            resultMap.put("human_review_required", true);
            resultMap.put("human_next_node", StateGraph.END); // 默认结束，等待人类反馈
            resultMap.put("review_instruction", generateReviewInstruction(aiAnalysisResult, riskScore, documentType));
            
            logger.info("Human review node prepared - Waiting for human reviewer input");
        }
        
        return resultMap;
    }

    /**
     * 根据审核动作和风险评分决定下一步流程
     */
    private String determineNextStep(String reviewAction, int riskScore) {
        switch (reviewAction.toLowerCase()) {
            case "approve":
            case "通过":
                return "approval_process";
                
            case "reject":
            case "拒绝":
                return "rejection_process";
                
            case "modify":
            case "修改":
                return "modification_process";
                
            default:
                logger.warn("Unknown review action: {}, defaulting to approval", reviewAction);
                return "approval_process";
        }
    }

    /**
     * 生成给人类审核员的指导信息
     */
    private String generateReviewInstruction(String aiAnalysisResult, int riskScore, String documentType) {
        StringBuilder instruction = new StringBuilder();
        instruction.append("请审核以下AI分析结果：\n\n");
        instruction.append("文档类型：").append(documentType).append("\n");
        instruction.append("风险评分：").append(riskScore).append("/10\n\n");
        
        if (riskScore >= 8) {
            instruction.append("⚠️ 高风险文档，请仔细审核！\n");
        } else if (riskScore >= 6) {
            instruction.append("⚡ 中等风险文档，建议谨慎处理。\n");
        } else {
            instruction.append("✅ 低风险文档，可正常处理。\n");
        }
        
        instruction.append("\nAI分析结果摘要：\n");
        instruction.append(aiAnalysisResult.length() > 200 ? 
            aiAnalysisResult.substring(0, 200) + "..." : aiAnalysisResult);
        
        instruction.append("\n\n请选择审核动作：");
        instruction.append("\n- approve/通过：同意文档通过");
        instruction.append("\n- reject/拒绝：拒绝文档");
        instruction.append("\n- modify/修改：需要修改后重审");
        
        return instruction.toString();
    }
}
