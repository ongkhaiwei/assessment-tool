package org.veritas.assessment.biz.entity.questionnaire;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.BeanUtils;
import org.veritas.assessment.biz.action.EditMainQuestionAction;
import org.veritas.assessment.biz.constant.AssessmentStep;
import org.veritas.assessment.biz.constant.Principle;
import org.veritas.assessment.common.exception.HasBeenModifiedException;
import org.veritas.assessment.common.exception.IllegalRequestException;
import org.veritas.assessment.common.exception.NotFoundException;
import org.veritas.assessment.common.handler.TimestampHandler;
import org.veritas.assessment.system.entity.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@TableName(autoResultMap = true)
@Slf4j
public class QuestionnaireVersion implements Comparable<QuestionnaireVersion> {
    /**
     * Version id
     */
    @TableId(type = IdType.INPUT)
    private Long vid;

    private Integer projectId;

    private Integer modelArtifactVid;

    private Integer creatorUserId;

    @TableField(typeHandler = TimestampHandler.class, jdbcType = JdbcType.VARCHAR)
    private Date createdTime;

    private String message;

    private Boolean exported;

    @TableField(exist = false)
    private List<QuestionNode> mainQuestionNodeList;

    @Override
    public int compareTo(QuestionnaireVersion o) {
        if (this.projectId == null) {
            throw new IllegalStateException();
        }
        return this.getVid().compareTo(o.vid);
    }


    public List<QuestionNode> getMainQuestionNodeList() {
        return mainQuestionNodeList == null ? Collections.emptyList() : mainQuestionNodeList;
    }

    public QuestionnaireVersion(int creatorUserId,
                                int projectId,
                                Date createdTime,
                                TemplateQuestionnaire templateQuestionnaire,
                                Supplier<Long> idGenerator) {
        this.mainQuestionNodeList = templateQuestionnaire.getMainQuestionList().stream()
                .map(QuestionNode::createFromTemplate)
                .collect(Collectors.toList());
        this.init(creatorUserId, projectId, createdTime, idGenerator);
    }

    public QuestionnaireVersion(int creatorUserId,
                                int projectId,
                                Date createdTime,
                                QuestionnaireVersion old,
                                Supplier<Long> idGenerator) {
        this.mainQuestionNodeList = old.getMainQuestionNodeList().stream()
                .map(QuestionNode::createFromOther)
                .collect(Collectors.toList());


        this.init(creatorUserId, projectId, createdTime, idGenerator);

    }

    public List<QuestionMeta> findAllQuestionMetaList() {
        if (mainQuestionNodeList == null || mainQuestionNodeList.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuestionMeta> list = new ArrayList<>();
        for (QuestionNode node : mainQuestionNodeList) {
            list.add(node.getMeta());
            for (QuestionNode subNode : node.getSubList()) {
                list.add(subNode.getMeta());
            }
        }
        return Collections.unmodifiableList(list);
    }

    public List<QuestionNode> finAllQuestionNodeList() {
        if (mainQuestionNodeList == null || mainQuestionNodeList.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuestionNode> list = new ArrayList<>();
        for (QuestionNode node : mainQuestionNodeList) {
            list.add(node);
            list.addAll(node.getSubList());
        }
        return Collections.unmodifiableList(list);
    }

    public List<QuestionVersion> finAllQuestionVersionList() {
        if (mainQuestionNodeList == null || mainQuestionNodeList.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuestionVersion> list = new ArrayList<>();
        for (QuestionNode node : mainQuestionNodeList) {
            list.add(node.getQuestionVersion());
            for (QuestionNode subNode : node.getSubList()) {
                list.add(subNode.getQuestionVersion());
            }
        }
        return Collections.unmodifiableList(list);
    }

    public void fill(List<QuestionNode> allNodeList, List<QuestionMeta> allMetaList, List<QuestionVersion> allQuestions) {
        Map<Long, QuestionMeta> metaMap = allMetaList.stream()
                .collect(Collectors.toMap(QuestionMeta::getId, meta -> meta));
        Map<Long, QuestionVersion> vidToQuestionMap = allQuestions.stream()
                .collect(Collectors.toMap(QuestionVersion::getVid, q -> q));

        for (QuestionNode node : allNodeList) {
            node.setMeta(metaMap.get(node.getQuestionId()));
            node.setQuestionVersion(vidToQuestionMap.get(node.getQuestionVid()));
        }

        List<QuestionNode> mainNodeList = allNodeList.stream()
                .filter(QuestionNode::isMain)
                .sorted()
                .collect(Collectors.toList());
        List<QuestionNode> subNodeList = allNodeList.stream()
                .filter(QuestionNode::isSub)
                .sorted()
                .collect(Collectors.toList());

        mainNodeList.forEach(node -> {
            node.setSubList(subNodeList);
        });
        this.mainQuestionNodeList = mainNodeList;
    }

    private void init(int creatorUserId, int projectId, Date createdTime, Supplier<Long> idGenerator) {
        Long questionnaireVid = idGenerator.get();
        // user, created time, project
        this.setProjectId(projectId);
        this.setCreatorUserId(creatorUserId);
        this.setCreatedTime(createdTime);
        this.setMessage(String.format("Created by user[%s], at %s", creatorUserId, createdTime));

        // questionnaire id
        this.setVid(questionnaireVid);
        for (QuestionNode node : mainQuestionNodeList) {
            node.initGenericPropertiesWithSubs(creatorUserId, projectId, questionnaireVid, createdTime);
        }
        // question id
        for (QuestionNode node : mainQuestionNodeList) {
            node.initQuestionId(idGenerator);
        }
        // question version id
        for (QuestionNode node : mainQuestionNodeList) {
            node.initQuestionVid(idGenerator);
        }
    }

    public Map<Principle, Map<AssessmentStep, List<QuestionNode>>> structure() {
        Map<Principle, Map<AssessmentStep, List<QuestionNode>>> map = new LinkedHashMap<>();
        for (Principle principle : Principle.values()) {
            Map<AssessmentStep, List<QuestionNode>> stepNode = new LinkedHashMap<>();
            for (AssessmentStep step : AssessmentStep.values()) {
                List<QuestionNode> questionNodeList = this.mainQuestionNodeList.stream()
                        .filter(q -> principle == q.getPrinciple() && step == q.getStep())
                        .sorted().collect(Collectors.toList());
                stepNode.put(step, questionNodeList);
            }
            map.put(principle, Collections.unmodifiableMap(stepNode));
        }
        return Collections.unmodifiableMap(map);
    }

    public List<QuestionNode> find(Principle principle, AssessmentStep step) {
        Objects.requireNonNull(principle);
        Objects.requireNonNull(step);
        return this.mainQuestionNodeList.stream()
                .filter(q -> principle == q.getPrinciple() && step == q.getStep())
                .sorted().collect(Collectors.toList());
    }

    public List<QuestionNode> findMainQuestion(Principle principle) {
        Objects.requireNonNull(principle);
        return this.mainQuestionNodeList.stream()
                .filter(q -> principle == q.getPrinciple())
                .sorted().collect(Collectors.toList());
    }

    public QuestionNode findMainQuestionById(long questionId) {
        return mainQuestionNodeList.stream().filter(q -> q.getQuestionId() == questionId)
                .findFirst().orElse(null);
    }

    // serial example: F1
    public QuestionNode findMainQuestionBySerial(String serial) {
        return mainQuestionNodeList.stream().filter(node -> StringUtils.equals(node.serial(), serial))
                .findFirst().orElse(null);
    }

    public QuestionNode findNodeByQuestionId(long questionId) {
        List<QuestionNode> all = this.finAllQuestionNodeList();
        return all.stream()
                .filter(node -> questionId == node.getQuestionId())
                .findFirst()
                .orElse(null);
    }

    public void configureQuestionnaireVid(long newVid) {
        this.setVid(newVid);
        this.finAllQuestionNodeList().forEach(node -> node.setQuestionnaireVid(newVid));
    }

    // create a new version for questionnaire.
    public QuestionnaireVersion createNewVersion(User operator, Date now, Supplier<Long> idSupplier) {
        Objects.requireNonNull(operator);
        if (now == null) {
            now = new Date();
        }
        Long newVid = idSupplier.get();
        QuestionnaireVersion newQuestionnaire = new QuestionnaireVersion();
        BeanUtils.copyProperties(this, newQuestionnaire);
        newQuestionnaire.setCreatedTime(now);
        newQuestionnaire.setCreatorUserId(operator.getId());
        newQuestionnaire.mainQuestionNodeList = QuestionNode.createNewList(this.mainQuestionNodeList);
        newQuestionnaire.configureQuestionnaireVid(newVid);
        return newQuestionnaire;
    }

    synchronized
    public void addMainQuestion(QuestionNode main) {
        main.setSerialOfPrinciple(Integer.MAX_VALUE);
        List<QuestionNode> list = new ArrayList<>(this.getMainQuestionNodeList().size());
        list.addAll(this.getMainQuestionNodeList());
        list.add(main);
        list = list.stream().sorted((a, b) -> {
            int result = a.getPrinciple().compareTo(b.getPrinciple());
            if (result == 0) {
                result = a.getStep().compareTo(b.getStep());
            }
            if (result == 0) {
                return a.getSerialOfPrinciple().compareTo(b.getSerialOfPrinciple());
            }
            return result;
        }).collect(Collectors.toList());
        Principle principle = main.getPrinciple();
        int serial = 0;
        for (QuestionNode questionNode : list) {
            if (principle == questionNode.getPrinciple()) {
                questionNode.configureSerialOfPrinciple(serial);
                ++serial;
            }
        }
        main.configureQuestionnaireVid(this.getVid());
        main.initAddStartQuestionnaireVid(this.getVid());

        this.setMainQuestionNodeList(list);
    }

    synchronized
    public void deleteMainQuestion(Long questionId) {
        Objects.requireNonNull(questionId);
        QuestionNode toDelete = this.findMainQuestionById(questionId);
        if (toDelete == null) {
            throw new IllegalRequestException("The question not existing.");
        }
        List<QuestionNode> list = new ArrayList<>(this.getMainQuestionNodeList().size());
        for (QuestionNode questionNode : this.getMainQuestionNodeList()) {
            if (questionNode != toDelete) {
                list.add(questionNode);
            }
            boolean samePrinciple = questionNode.getPrinciple() == toDelete.getPrinciple();
            if (samePrinciple) {
                if (questionNode.getSerialOfPrinciple() > toDelete.getSerialOfPrinciple()) {
                    questionNode.setSerialOfPrinciple(questionNode.getSerialOfPrinciple() - 1);
                }
            }
        }
        this.mainQuestionNodeList = list;
    }

    synchronized
    public void reorder(Principle principle, AssessmentStep step, List<Long> newOrderQuestionIdList) {
        List<QuestionNode> currentMainList = this.find(principle, step);

        List<QuestionNode> newOrderedList = new ArrayList<>(currentMainList.size());

        int seq = 0;
        for (Long questionId : newOrderQuestionIdList) {
            Optional<QuestionNode> optional = currentMainList.stream()
                    .filter(q -> Objects.equals(q.getQuestionId(), questionId))
                    .findFirst();
            if (optional.isPresent()) {
                QuestionNode questionNode = optional.get();
                newOrderedList.add(questionNode);
                questionNode.configureSerialOfPrinciple(seq);
                ++seq;
            } else {
                log.warn("Not found the question[{}]. It may be deleted.", questionId);
            }
        }
        if (newOrderedList.size() != currentMainList.size()) {
            throw new HasBeenModifiedException("Some questions have been added.");
        }
        this.mainQuestionNodeList = this.mainQuestionNodeList.stream().sorted().collect(Collectors.toList());
    }

    // TODO: 2023/2/13 to private
    public void deleteSubQuestions(Long mainQuestionId, List<Long> deleteList) {
        Objects.requireNonNull(mainQuestionId);
        Objects.requireNonNull(deleteList);
        if (deleteList.isEmpty()) {
            return;
        }
        QuestionNode main = this.findMainQuestionById(mainQuestionId);
        if (main == null) {
            log.warn("");
            return;
        }

        List<QuestionNode> newSubList = new ArrayList<>();
        int subSerial = 1;
        for (QuestionNode sub : main.getSubList()) {
            if (deleteList.contains(sub.getQuestionId())) {
                continue;
            }
            sub.setSubSerial(subSerial);
            ++subSerial;
            newSubList.add(sub);
        }
        main.setSubList(newSubList);
    }

    public List<QuestionNode> addSub(Long mainQuestionId, List<String> subQuestionList) {
        QuestionNode main = this.findMainQuestionById(mainQuestionId);
        if (main == null) {
            throw new IllegalArgumentException("Not found the main questionId.");
        }
        return null;
    }

    public List<QuestionNode> edite(Long mainQuestionId, Map<Long, String> editMap, Supplier<Long> idGenerator) {
        Objects.requireNonNull(mainQuestionId);
        Objects.requireNonNull(editMap);
        Objects.requireNonNull(idGenerator);
        if (editMap.isEmpty()) {
            return Collections.emptyList();
        }
        QuestionNode main = this.findMainQuestionById(mainQuestionId);
        if (main == null) {
            throw new NotFoundException("Not found the question.");
        }
        Map<Long, QuestionNode> allNode = main.toIdNodeMap();
        List<QuestionNode> editedNodeList = new ArrayList<>();

        // create version
        for (Map.Entry<Long, String> entry : editMap.entrySet()) {
            Long questionId = entry.getKey();
            String questionContent = entry.getValue();

            QuestionNode node = allNode.get(questionId);
            if (node == null) {
                throw new NotFoundException("Not found the question.");
            }

            QuestionVersion newVersion = node.getQuestionVersion().clone();
            newVersion.setVid(idGenerator.get());
            newVersion.setContent(questionContent);
            newVersion.setContentEditTime(this.getCreatedTime());
            newVersion.setContentEditUserId(this.creatorUserId);

            node.setQuestionVersion(newVersion);
            node.setQuestionVid(newVersion.getVid());

            //meta
            QuestionMeta meta = node.getMeta();
            meta.setCurrentVid(newVersion.getVid());

            editedNodeList.add(node);
        }
        return editedNodeList;
    }

    public void edit(EditMainQuestionAction action, Supplier<Long> idGenerator) {
        Long mainQuestionId = action.getMainQuestionId();
        QuestionNode main = this.findMainQuestionById(mainQuestionId);
        QuestionNode basedMain = action.getBasedQuestionnaire().findMainQuestionById(mainQuestionId);
        if (!main.isQuestionContentSame(basedMain)) {
            throw new HasBeenModifiedException("The question has been modified.");
        }
        // delete
        List<Long> deleteList = action.getDeletedSubList();
        List<QuestionNode> newSubList = new ArrayList<>();
        int subSerial = 1;
        for (QuestionNode sub : main.getSubList()) {
            if (deleteList.contains(sub.getQuestionId())) {
                continue;
            }
            sub.setSubSerial(subSerial);
            ++subSerial;
            newSubList.add(sub);
        }




    }

}
