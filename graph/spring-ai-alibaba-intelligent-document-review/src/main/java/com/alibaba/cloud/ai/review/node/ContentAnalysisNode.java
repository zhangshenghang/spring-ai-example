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
 * 内容分析节点
 * 对文档内容进行初步分析，识别文档类型、主要内容和基本结构
 * 
 * @author Jast
 */
public class ContentAnalysisNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ContentAnalysisNode.class);

    private static final PromptTemplate CONTENT_ANALYSIS_PROMPT = new PromptTemplate(
        """
        你是一个专业的文档分析专家。请对以下文档进行详细的内容分析：

        文档类型：{document_type}
        紧急程度：{urgency_level}
        文档内容：{document_content}

        请从以下几个方面进行分析：
        1. 文档主题和目的
        2. 内容结构和逻辑
        3. 关键信息点
        4. 语言表达质量
        5. 完整性评估

        请以JSON格式返回分析结果，包含：
        - summary: 文档摘要
        - main_topics: 主要话题列表
        - key_points: 关键点列表
        - structure_quality: 结构质量评分(1-10)
        - language_quality: 语言质量评分(1-10)
        - completeness: 完整性评分(1-10)
        - initial_concerns: 初步发现的问题列表

        分析结果：
        """
    );

    private final ChatClient chatClient;

    public ContentAnalysisNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("Content analysis node is running...");

        String documentContent = state.value("document_content", "");
        String documentType = state.value("document_type", "general");
        String urgencyLevel = state.value("urgency_level", "normal");

        logger.info("Analyzing document - Type: {}, Urgency: {}, Content length: {}", 
                   documentType, urgencyLevel, documentContent.length());

        Flux<ChatResponse> chatResponseFlux = this.chatClient.prompt()
                .user(user -> user.text(CONTENT_ANALYSIS_PROMPT.getTemplate())
                        .param("document_content", documentContent)
                        .param("document_type", documentType)
                        .param("urgency_level", urgencyLevel))
                .stream()
                .chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("content_analysis_stream")
                .startingState(state)
                .mapResult(response -> {
                    String analysisResult = response.getResult().getOutput().getText();
                    logger.info("Content analysis completed, result length: {}", analysisResult.length());
                    return Map.of("content_analysis_result", analysisResult);
                })
                .build(chatResponseFlux);

        return Map.of("content_analysis_result", generator);
    }
}
