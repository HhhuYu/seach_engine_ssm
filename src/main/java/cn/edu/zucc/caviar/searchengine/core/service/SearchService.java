package cn.edu.zucc.caviar.searchengine.core.service;

import cn.edu.zucc.caviar.searchengine.core.pojo.Document;
import cn.edu.zucc.caviar.searchengine.core.pojo.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SearchService {
    public Response keywordSearch(String keyword, Integer page, String recommendNumber);

    public long documentPageCount(String keyword);

    public Set<Document> documentsInPage(long currentPage,long count, List<Document>documentList);

    public Map<String, Double> checkSpell(String token, boolean isPinyin);

    public String highlight(String src, List<String> keyword);

    public String generateSnippets(String src, List<String> keyword, String type);

    public Set<Document> recommendDocuments(String recommendNumber);

}
