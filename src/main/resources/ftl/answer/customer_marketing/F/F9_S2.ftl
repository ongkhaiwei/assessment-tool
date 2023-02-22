<#--F9.2 What are the values of the fairness metrics and the uncertainties in those metrics?-->

<h3>Group Fairness</h3>

<div>
    The table below lists the values and respective uncertainties of fairness metrics.
    The uncertainties in the fairness metrics are measured using bootstrap methods with 50 replications and
    5-95% confidence intervals used and the plus-minus intervals representing two standard deviations.
    The primary fairness metric is marked in red.
</div>

<div>
    Fairness threshold is calculated based on fairness threshold input.
    Fairness conclusion will be generated by comparing fairness threshold and absolute difference between the fairness metrics
    and neutral position. Note that if the metric is ratio based, neutral position is 1;
    if metric is parity based, neutral position is 0);
    if the absolute difference is lower than the fairness threshold, the fairness conclusion would be fair.
</div>

<#list fairness.featureMap as feature_name, feature>
    <div>
        <div class="table_box">
        <div>
        <big>
        Fairness metrics for <b>${feature_name}</b>
        </big>
        </div>
        <table>
            <thead>
            <tr>
                <th>Fairness Metric</th>
                <th>Value</th>
            </tr>
            </thead>
            <tbody>
            <#list feature.fairMetricValueListMap as metricName, valueList>
                <tr <#if fairness.isFairMetric(metricName)> class="fair_metric_row"</#if>>
                    <td class="metric_name">${metricName}</td>
                    <td class="metric_value">${feature.faireMetricValueFormat(valueList)}</td>
                </tr>
            </#list>
            </tbody>
        </table>
        </div>

        <div>Fairness Threshold Input: <b>${fairness.fairnessInit.fairThresholdInput}%</b></div>
        <div>Fairness Threshold: <b>${feature.fairThreshold}</b></div>
        <div>Fairness Conclusion: <b>${feature.fairnessConclusion}</b></div>

    </div>
</#list>



<#if fairness.individualFairness?consistencyScore??>
<h3>Individual Fairness</h3>
For individual fairness, the consistency score is ${fairness.individualFairness.consistencyScore}.
</#if>