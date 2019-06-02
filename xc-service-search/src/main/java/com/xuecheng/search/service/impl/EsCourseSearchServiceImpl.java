package com.xuecheng.search.service.impl;

import com.xuecheng.framework.domain.course.CoursePub;
import com.xuecheng.framework.domain.course.response.CourseCode;
import com.xuecheng.framework.domain.search.CourseSearchParam;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.search.service.EsCourseSearchService;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: 98050
 * @Time: 2019-05-31 16:35
 * @Feature:
 */
@Service
public class EsCourseSearchServiceImpl implements EsCourseSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EsCourseSearchServiceImpl.class);

    @Value("${xuecheng.elasticsearch.course.index}")
    private String esIndex;

    @Value("${xuecheng.elasticsearch.course.type}")
    private String esType;

    @Value("${xuecheng.elasticsearch.course.source_field}")
    private String esSourceField;

    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @Override
    public QueryResponseResult list(int page, int size, CourseSearchParam courseSearchParam) {
        String[] sourceFields = esSourceField.split(",");
        //1.设置索引
        SearchRequest searchRequest = new SearchRequest(esIndex);
        //2.设置类型
        searchRequest.types(esType);
        //3.构建搜索对象
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //4.字段过滤
        searchSourceBuilder.fetchSource(sourceFields, new String[]{});
        //5.构建布尔查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //6.按关键字搜索
        if (StringUtils.isNotEmpty(courseSearchParam.getKeyword())){
            //6.1匹配关键字
            MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(courseSearchParam.getKeyword(), "name","teachplan","description");
            multiMatchQueryBuilder.minimumShouldMatch("70%");
            //6.2提升name字段的Boost值
            multiMatchQueryBuilder.field("name", 10);
            boolQueryBuilder.must(multiMatchQueryBuilder);
        }
        //7.按分类和难度等级过滤
        if (StringUtils.isNotEmpty(courseSearchParam.getMt())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("mt", courseSearchParam.getMt()));
        }
        if (StringUtils.isNotEmpty(courseSearchParam.getSt())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("st", courseSearchParam.getSt()));
        }
        if (StringUtils.isNotEmpty(courseSearchParam.getGrade())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("grade", courseSearchParam.getGrade()));
        }
        //8.高亮与分页
        //8.1分页
        if (page <= 0){
            page = 1;
        }
        if (size <= 0){
            size = 20;
        }
        int start = (page - 1) * size;
        searchSourceBuilder.from(start);
        searchSourceBuilder.size(size);
        //8.2高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.preTags("<font class='eslight'>");
        highlightBuilder.postTags("</font>");
        highlightBuilder.fields().add(new HighlightBuilder.Field("name"));
        highlightBuilder.fields().add(new HighlightBuilder.Field("description"));
        searchSourceBuilder.highlighter(highlightBuilder);
        //9.布尔查询
        searchSourceBuilder.query(boolQueryBuilder);
        //10.请求搜索
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = null;
        try {
            searchResponse = this.restHighLevelClient.search(searchRequest);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("xuecheng search error..{}",e.getMessage());
            return new QueryResponseResult(CommonCode.SUCCESS, new QueryResult<CoursePub>());
        }
        //11.结果集处理
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        //11.1记录总数
        long totalHits = hits.getTotalHits();
        //11.2数据列表
        List<CoursePub> list = new ArrayList<>();
        for (SearchHit hit : searchHits){
            CoursePub coursePub = new CoursePub();
            //取出source
            Map<String,Object> sourseAsMap = hit.getSourceAsMap();
            //取出名称
            String name = (String) sourseAsMap.get("name");
            coursePub.setName(name);
            //图片
            String pic = (String) sourseAsMap.get("pic");
            coursePub.setPic(pic);
            //价格
            Float price = null;
            if (sourseAsMap.get("price") != null){
                price = Float.parseFloat((String) sourseAsMap.get("price"));
            }
            coursePub.setPrice(price);
            //旧价格
            Float oldPrice = null;
            if (sourseAsMap.get("price_old") != null){
                oldPrice = Float.parseFloat((String) sourseAsMap.get("price_old"));
            }
            coursePub.setPrice_old(oldPrice);
            list.add(coursePub);
        }
        QueryResult<CoursePub> queryResult = new QueryResult<>();
        queryResult.setList(list);
        queryResult.setTotal(totalHits);

        return new QueryResponseResult(CommonCode.SUCCESS,queryResult);
    }
}