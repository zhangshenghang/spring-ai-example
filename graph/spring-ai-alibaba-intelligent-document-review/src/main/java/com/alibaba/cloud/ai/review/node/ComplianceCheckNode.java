package com.alibaba.cloud.ai.review.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingChatGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 合规性检查节点
 * 检查文档是否符合相关法规、标准和公司政策
 * 
 * @author yingzi
 * @since 2025/6/26
 */
public class ComplianceCheckNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceCheckNode.class);

    private static final PromptTemplate COMPLIANCE_CHECK_PROMPT = new PromptTemplate(
        """
        你是一个专业的合规性审查专家。请对以下文档进行合规性检查：

        文档类型：{document_type}
        文档内容：{document_content}
        初步分析结果：{content_analysis_result}

        请根据文档类型检查以下合规性要求：

        通用要求：
        1. 信息准确性和真实性
        2. 格式规范性
        3. 必要信息完整性
        4. 语言规范性

        根据文档类型的特殊要求：
        - 合同类：法律条款完整性、权责明确性、风险条款合理性
        - 技术文档：技术标准符合性、安全要求、版本管理
        - 财务报告：数据准确性、审计要求、披露完整性
        - 政策文件：政策依据、执行可行性、影响评估

        请以JSON格式返回检查结果，包含：
        - compliance_score: 合规性评分(1-10)
        - passed_checks: 通过的检查项列表
        - failed_checks: 未通过的检查项列表
        - warnings: 警告项列表
        - recommendations: 改进建议列表
        - overall_status: 总体状态(compliant/non-compliant/needs-review)

        合规性检查结果：
        """
    );

    private final ChatClient chatClient;

    public ComplianceCheckNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Compliance check node is running...");

        String documentContent = state.value("document_content", "");
        String documentType = state.value("document_type", "general");
        String contentAnalysisResult = state.value("content_analysis_result", "");

        logger.info("Performing compliance check for document type: {}", documentType);

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(COMPLIANCE_CHECK_PROMPT.getTemplate())
                        .param("document_content", documentContent)
                        .param("document_type", documentType)
                        .param("content_analysis_result", contentAnalysisResult))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("compliance_check_stream")
                .startingState(state)
                .mapResult(response -> {
                    String complianceResult = response.getResult().getOutput().getText();
                    logger.info("Compliance check completed, result length: {}", complianceResult.length());
                    return Map.of("compliance_result", complianceResult);
                })
                .build(chatResponseFlux);

        return Map.of("compliance_result", generator);
    }
}
