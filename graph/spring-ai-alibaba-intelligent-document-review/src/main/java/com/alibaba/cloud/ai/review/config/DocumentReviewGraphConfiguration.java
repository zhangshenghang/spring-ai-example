package com.alibaba.cloud.ai.review.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.review.dispatcher.ReviewDecisionDispatcher;
import com.alibaba.cloud.ai.review.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 文档审核系统图形配置
 * 定义了完整的文档审核工作流：内容分析 -> 合规检查 -> 风险评估 -> 人工审核 -> 后续处理
 * 
 * @author yingzi
 * @since 2025/6/26
 */
@Configuration
public class DocumentReviewGraphConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReviewGraphConfiguration.class);

    @Bean
    public StateGraph documentReviewGraph(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            
            // 文档基本信息
            keyStrategyHashMap.put("document_content", new ReplaceStrategy());
            keyStrategyHashMap.put("document_type", new ReplaceStrategy());
            keyStrategyHashMap.put("urgency_level", new ReplaceStrategy());
            keyStrategyHashMap.put("thread_id", new ReplaceStrategy());

            // AI 分析结果
            keyStrategyHashMap.put("content_analysis_result", new ReplaceStrategy());
            keyStrategyHashMap.put("compliance_result", new ReplaceStrategy());
            keyStrategyHashMap.put("risk_score", new ReplaceStrategy());
            keyStrategyHashMap.put("ai_analysis_result", new ReplaceStrategy());
            keyStrategyHashMap.put("issues_found", new ReplaceStrategy());
            keyStrategyHashMap.put("recommendations", new ReplaceStrategy());

            // 人工审核反馈
            keyStrategyHashMap.put("review_action", new ReplaceStrategy());
            keyStrategyHashMap.put("reviewer_comments", new ReplaceStrategy());
            keyStrategyHashMap.put("suggested_changes", new ReplaceStrategy());
            keyStrategyHashMap.put("human_next_node", new ReplaceStrategy());

            // 最终结果
            keyStrategyHashMap.put("final_status", new ReplaceStrategy());
            keyStrategyHashMap.put("approval_reason", new ReplaceStrategy());
            keyStrategyHashMap.put("rejection_reason", new ReplaceStrategy());
            keyStrategyHashMap.put("final_report", new ReplaceStrategy());

            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                // 添加所有节点
                .addNode("content_analysis", node_async(new ContentAnalysisNode(chatClientBuilder)))
                .addNode("compliance_check", node_async(new ComplianceCheckNode(chatClientBuilder)))
                .addNode("risk_assessment", node_async(new RiskAssessmentNode(chatClientBuilder)))
                .addNode("human_review", node_async(new HumanReviewNode()))
                .addNode("approval_process", node_async(new ApprovalProcessNode(chatClientBuilder)))
                .addNode("rejection_process", node_async(new RejectionProcessNode(chatClientBuilder)))
                .addNode("modification_process", node_async(new ModificationProcessNode(chatClientBuilder)))
                .addNode("final_report", node_async(new FinalReportNode(chatClientBuilder)))

                // 定义流程路径
                .addEdge(StateGraph.START, "content_analysis")           // 开始 -> 内容分析
                .addEdge("content_analysis", "compliance_check")         // 内容分析 -> 合规检查
                .addEdge("compliance_check", "risk_assessment")          // 合规检查 -> 风险评估
                .addEdge("risk_assessment", "human_review")              // 风险评估 -> 人工审核

                // 人工审核后的条件分支
                .addConditionalEdges("human_review", 
                    AsyncEdgeAction.edge_async(new ReviewDecisionDispatcher()), 
                    Map.of(
                        "approval_process", "approval_process",
                        "rejection_process", "rejection_process",
                        "modification_process", "modification_process",
                        StateGraph.END, StateGraph.END
                    ))

                // 各种处理流程都流向最终报告
                .addEdge("approval_process", "final_report")
                .addEdge("rejection_process", "final_report")
                .addEdge("modification_process", "final_report")
                .addEdge("final_report", StateGraph.END);

        // 打印 PlantUML 流程图
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "Document Review Workflow");
        logger.info("\n=== Document Review Workflow UML ===");
        logger.info(representation.content());
        logger.info("=======================================\n");

        return stateGraph;
    }
}
