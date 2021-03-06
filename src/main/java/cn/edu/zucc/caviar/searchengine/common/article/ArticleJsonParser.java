package cn.edu.zucc.caviar.searchengine.common.article;

import cn.edu.zucc.caviar.searchengine.common.query.spell.PinyinUtil;
import cn.edu.zucc.caviar.searchengine.common.query.spell.SoundexCoder;
import cn.edu.zucc.caviar.searchengine.common.utils.HbaseUtil;
import cn.edu.zucc.caviar.searchengine.common.utils.HighLeveRestClientUtil;
import cn.edu.zucc.caviar.searchengine.common.utils.RedisUtil;
import cn.edu.zucc.caviar.searchengine.core.pojo.Document;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

public class ArticleJsonParser {

    public static HbaseUtil hbaseUtil;
    public static RedisUtil redisUtil;
    public static HighLeveRestClientUtil highLeveRestClientUtil;
    public static final String SAVE_CONTENT = "Content";
    public static final String CREATE_INDEX = "Index";
    public static final String SAVE_CONTENT_IN_ES = "Es";


    /***
     * 按行读取doc Json数据并存入hbase
     * @param filePath json所在目录
     * @param operate 操作 建立内容 或 建立索引
     */
    public static void readContentJSON(String filePath, String operate) {
        JsonParser parser = new JsonParser();
        JsonObject object;

        try {
            String root = System.getProperty("user.dir");
            filePath = root + "/src/main/resources/" + filePath;

            BufferedReader bfReader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = bfReader.readLine()) != null) {
                object = (JsonObject) parser.parse(line);
                if (operate.equals(SAVE_CONTENT_IN_ES))
                    storeContentInEs(object);
                else if (operate.equals(SAVE_CONTENT))
                    storeContentInHbase(object);
                else if (operate.equals(CREATE_INDEX))
                    createIndex(object);

            }
            RedisUtil.pipeLineSync();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    /***
     * 保存Json格式的doc入hbase
     * @param article
     */
    public static void storeContentInEs(JsonObject article) {

        JsonObject docData = article.getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject();
        String docId = docData.get("id").getAsString();

        String[] contentSplit = docData.get("content").getAsString().split("\\n", 2);

        String title = contentSplit[0];
        String content = "";
        if (contentSplit.length == 2) {
            content = contentSplit[1];
        } else {
            content = contentSplit[0];
        }
        Document document=new Document();
        document.setContent(content);
        document.setDocId(docId);
        document.setTitle(title);
        highLeveRestClientUtil.puts.add(document);
    }

    /***
     * 保存Json格式的doc入hbase
     * @param article
     */
    public static void storeContentInHbase(JsonObject article) {

        JsonObject docData = article.getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject();
        String docId = docData.get("id").getAsString();
        String author = docData.get("posterScreenName").getAsString();

        System.out.println(docId);

        String[] contentSplit = docData.get("content").getAsString().split("\\n", 2);

        String title = contentSplit[0];
        String content = "";
        if (contentSplit.length == 2) {
//            System.out.println("title:" + title);
            content = contentSplit[1];
//            System.out.println("content:" + content);
        } else {
            content = contentSplit[0];
        }

        String favoriteCount = docData.get("favoriteCount").getAsJsonObject().get("$numberInt").getAsString();
        String likeCount = docData.get("likeCount").getAsJsonObject().get("$numberInt").getAsString();
        String commentCount = docData.get("commentCount").getAsJsonObject().get("$numberInt").getAsString();
        String shareCount = docData.get("shareCount").getAsJsonObject().get("$numberInt").getAsString();
        JsonArray images = docData.get("imageUrls").getAsJsonArray();
        String publishDate = docData.get("publishDateStr").getAsString();

//        System.out.println(publishDate);

        String imageUrls = "";

        for (int i = 0; i < images.size(); i++) {
            if (i == 0)
                imageUrls += images.get(i).getAsString();
            else
                imageUrls += ("," + images.get(i).getAsString());
        }

        hbaseUtil.putIntoList(docId, "imageUrls", imageUrls);
        hbaseUtil.putIntoList(docId, "docId", docId);
        hbaseUtil.putIntoList(docId, "title", title);
        hbaseUtil.putIntoList(docId, "author", author);
        hbaseUtil.putIntoList(docId, "content", content);
        hbaseUtil.putIntoList(docId, "favoriteCount", favoriteCount);
        hbaseUtil.putIntoList(docId, "likeCount", likeCount);
        hbaseUtil.putIntoList(docId, "commentCount", commentCount);
        hbaseUtil.putIntoList(docId, "shareCount", shareCount);
        hbaseUtil.putIntoList(docId, "publishDate", publishDate);

//        hbaseUtil.put(docId, "imageUrls", imageUrls);
//        hbaseUtil.put(docId, "docId", docId);
//        hbaseUtil.put(docId, "author", author);
//        hbaseUtil.put(docId, "title", title);
//        hbaseUtil.put(docId, "content", content);
//        hbaseUtil.put(docId, "favoriteCount", favoriteCount);
//        hbaseUtil.put(docId, "likeCount", likeCount);
//        hbaseUtil.put(docId, "commentCount", commentCount);
//        hbaseUtil.put(docId, "shareCount", shareCount);
//        hbaseUtil.put(docId, "publishDate", publishDate);

    }

    public static void createIndex(JsonObject keyWords) {
        String docId = keyWords.get("id").getAsString();
        for (String keyword : keyWords.get("keywords").getAsJsonObject().keySet()) {
            double score = keyWords.get("keywords").getAsJsonObject().get(keyword).getAsDouble();
            System.out.println(keyword);
            List<Pinyin> pinyinList = HanLP.convertToPinyinList(keyword);
            String soundexCode = SoundexCoder.soundex(pinyinList);
            System.out.println(score);
            redisUtil.insertIndex(keyword, docId, score);
            System.out.println(PinyinUtil.getPinyin(pinyinList));
            redisUtil.insertIndex(pinyinList.get(0).getShengmu().toString(), keyword, Double.valueOf(soundexCode));
        }

    }

    /***
     * 建立索引以及存储文章
     * @param args
     */
    public static void main(String args[]) throws Exception {
//        redisUtil = new RedisUtil();
//        hbaseUtil = new HbaseUtil();
        highLeveRestClientUtil = new HighLeveRestClientUtil();
//        readContentJSON("search_data/keywords4.json", CREATE_INDEX);
//        readContentJSON("search_data/xhs_note_item_final.json", SAVE_CONTENT);
        readContentJSON("search_data/xhs_note_item_final.json", SAVE_CONTENT_IN_ES);
        highLeveRestClientUtil.createList(highLeveRestClientUtil.puts);
//        HbaseUtil.flushPutList(hbaseUtil.puts);
    }
}
