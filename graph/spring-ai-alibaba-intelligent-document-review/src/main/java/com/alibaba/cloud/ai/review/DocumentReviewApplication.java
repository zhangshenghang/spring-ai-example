package com.alibaba.cloud.ai.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能文档审核系统应用启动类
 * 演示人类返回节点特性的真实场景：
 * 1. AI 初步分析文档内容和合规性
 * 2. 人类审核员确认 AI 分析结果
 * 3. 根据人类决策执行后续流程（通过/拒绝/修改建议）
 * 
 * @author yingzi
 * @since 2025/6/26
 */
@SpringBootApplication
public class DocumentReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentReviewApplication.class, args);
    }
}
