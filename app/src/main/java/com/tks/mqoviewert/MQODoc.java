package com.tks.mqoviewert;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Vector;

/**
 * Created by jun on 2016/11/29.
 */
public class MQODoc {
    private static final String TAG = "GLES20";

    private String[] mLines;         // ファイルを構成する1行1行からなる配列
    private int mIndex;         // 現在解析すべき行のインデックス
    private Material[] mMaterials;     // Materialチャンクの情報を管理するための変数
    private Vector<MQOObject> mObjects;       // Objectチャンクの情報を管理するための変数
    private int mNumVertices;   // 総頂点数
    private int mNumIndices;    // 総インデックス数

    // コンストラクタ
    public MQODoc() {
    }

    // 解析処理
    public boolean parse(String fileString) {
        String[] lines = fileString.split("\n");  // 行に分解し配列として格納
        this.mLines = new String[lines.length + 1];
        System.arraycopy(lines, 0, mLines, 0, lines.length);
        this.mLines[lines.length] = null;       // 最後にnullを追加しておく
        this.mIndex = 0;              // 行のインデックスを初期化
        this.mMaterials = null;       // Materialチャンクの情報を初期化
        this.mObjects = new Vector<MQOObject>(); // Objectチャンクの情報を初期化
        this.mNumVertices = 0;        // 総頂点数を0に
        this.mNumIndices = 0;         // 総インデックス数を0に

        // 1行ずつ解析していく
        String line; // 解析する行の文字列
        MQOObject obj;  // Objectチャンクを解析したもの
        while ((line = this.mLines[this.mIndex++]) != null) {
            if (line.indexOf("Scene") >= 0) {    // Sceneチャンクの読み飛ばし(使わない)
                this.skipToEndOfChunk();
                continue; // 次の行へ
            }
            if (line.indexOf("Material") >= 0) { // Materialチャンクの読み込み
                this.mMaterials = this.readMaterials(line);
                continue; // 次の行へ
            }
            if (line.indexOf("Object") >= 0) {   // Objectチャンクの読み込み
                if ((obj = this.readObjects()) != null) this.mObjects.add(obj);
                else return false;
                continue; // 次の行へ
            }
        }

        return true;
    }

    // Materialチャンクの読み込み
    public Material[] readMaterials(String line) {
        StringParser sp = new StringParser(line); // 行の中身を解析するStringParserオブジェクト

        // 「Material 2 {」の解析
        sp.skipToNextWord();            // "Material"を読み飛ばす(使わない)
        int n = sp.getInt();            // マテリアル数を取得
        Material[] materials = new Material[n];   // マテリアル情報を管理する配列を用意

        // 「"mat1" col(1.000 0.000 0.000 1.000) dif(0.800) ...  power(5.00)」の解析
        for (int i = 0; i < n; i++) {
            sp.init(this.mLines[this.mIndex++]); // もったいないのでspオブジェクトを再利用
            sp.skipToNextWord();      // Material名を読み飛ばす(使わない)
            String word = null;
            while ((word = sp.getWord()) != null) {
                if (word.equals("col")) {
                    float r = sp.getFloat();
                    float g = sp.getFloat();
                    float b = sp.getFloat();
                    float a = sp.getFloat();
                    materials[i] = new Material(r, g, b, a); // Materialオブジェクトにまとめて管理
                } else if (word.equals("tex")) {
                    materials[i].texture = sp.getWord();
                }
            }
        }
        this.skipToEndOfChunk();  // チャンク終了まで読み飛ばす

        return materials;         // 解析結果を返す
    }

    // Objectチャンクの読み込み
    public MQOObject readObjects() {
        // 「Object "obj1" {」は使用しないのでline引数は要らない
        MQOObject mqoObject = new MQOObject(); // Objectチャンクの情報を管理するオブジェクトを用意

        // Objectチャンクの中身を1行ずつ解析
        String line;  // 解析する行の文字列
        while ((line = this.mLines[this.mIndex++]) != null) {
            if (line.indexOf("facet") >= 0) continue;      // faceが部分文字列のため先に読み飛ばす
            if (line.indexOf("color_type") >= 0) continue; // colorが部分文字列のため先に読み飛ばす
            if (line.indexOf("color") >= 0) {              // colorパラメータの読み込み
                mqoObject.color = this.readColor(line);      // 「color 0.898 0.498 0.698」の解析
                continue;
            }
            if (line.indexOf("shading") >= 0) {            // shadingパラメータの読み込み
                mqoObject.shading = this.readShading(line);  // 「 shading 0」の解析
                continue;
            }
            if (line.indexOf("vertex") >= 0) {             // vertexチャンクの読み込み
                mqoObject.vertices = this.readVertices(line);// 「vertex 8 {」の解析
                continue;
            }
            if (line.indexOf("face") >= 0) {               // faceチャンクの読み込み
                mqoObject.faces = this.readFaces(line);      // 「face 6 {」の解析
                continue;
            }
            if (line.indexOf("}") >= 0) {
                break;  // チャンクの終わり
            }
        }

        // 読み込んだFaceチャンクの法線を計算
        for (int i = 0, len = mqoObject.faces.length; i < len; i++) {
            Face face = mqoObject.faces[i];
            face.setNormal(mqoObject.vertices);
        }

        return mqoObject;
    }

    // 色情報の読み込み(「color 0.898 0.498 0.698」の解析)
    public Material readColor(String line) {
        StringParser sp = new StringParser(line);
        sp.skipToNextWord();  // "color"を読み飛ばす
        float r = sp.getFloat();
        float g = sp.getFloat();
        float b = sp.getFloat();

        return new Material(r, g, b, 1.0f);
    }

    // Shading方法の読み込み(「 shading 0」の解析)
    public int readShading(String line) {
        StringParser sp = new StringParser(line);
        sp.skipToNextWord();  // "shading"を読み飛ばす

        return sp.getInt();   // 数字の取り出し
    }

    // vertexチャンクの読み込み
    public Vertex[] readVertices(String line) {
        StringParser sp = new StringParser(line);
        // 「vertex 8 {」の解析
        sp.skipToNextWord();         // "vertex"を読み飛ばす
        int n = sp.getInt();         // 頂点数を取得
        Vertex[] vertices = new Vertex[n]; // 頂点座標を管理するための配列を用意
        // 頂点座標の取り出し(「-100.0000 100.0000 100.0000」の解析)
        for (int i = 0; i < n; i++) {
            sp.init(this.mLines[this.mIndex++]);
            float x = sp.getFloat();
            float y = sp.getFloat();
            float z = sp.getFloat();
            vertices[i] = new Vertex(x, y, z);
        }
        this.skipToEndOfChunk(); // チャンク終了まで読み飛ばす

        return vertices;
    }

    // face(面情報)チャンクの読み込み
    public Face[] readFaces(String line) {
        StringParser sp = new StringParser(line);
        // 「 face 6 {」の解析
        sp.skipToNextWord();             // "face"の読み飛ばし
        int numFaces = sp.getInt();     // フェイス数の取得
        Face[] faces = new  Face[numFaces]; // face情報を管理するための配列を用意

        // 「4 V(0 2 3 1) M(0) UV(0.00000 0.00000 ... 1.00000)」の解析
        for (int i = 0; i < numFaces; i++) {
            sp.init(this.mLines[this.mIndex++]);
            int n = sp.getInt();  // 面の頂点数
            if (n != 3 && n != 4) { Log.w(TAG, "error face"); continue; }
            short[] vIndices = new short[n]; // インデックスを保存するための配列を用意
            float[] uvs = new float[2 * n];
            int mIndex = -1;
            String word;
            while ((word = sp.getWord()) != null) {
                if (word.equals("V")) {
                    for (int j = 0; j < n; j++) vIndices[j] = (short)sp.getInt();
                } else if (word.equals("M")) {
                    mIndex = sp.getInt();
                } else if (word.equals("UV")) {
                    for (int j = 0; j < 2 * n; j++) uvs[j] = sp.getFloat();
                }
            }
            faces[i] = new Face(vIndices, mIndex, uvs);
            this.updateNumVertices(n); // 総頂点数と総インデックス数を更新
        }
        this.skipToEndOfChunk();     // チャンク終了まで読み飛ばす

        return faces;
    }

    // 総頂点数と総インデックス数を更新する
    public void updateNumVertices(int numIndices) {
        this.mNumVertices += numIndices;         // 総頂点数に加算
        if (numIndices == 3)
            this.mNumIndices += numIndices;     // つまり3(三角形で描画)
        else // numIndicesが4の場合(四角形)
            this.mNumIndices += numIndices * 2; // つまり6(三角形2つで描画)
    }

    // チャンク終了まで読み飛ばす
    public void skipToEndOfChunk() {
        String line;
        while ((line = this.mLines[this.mIndex++]) != null)
            if (line.indexOf('}') >= 0) break;
    }

    //------------------------------------------------------------------------------
    // モデルの描画用の情報を取得する
    public ArrayList<DrawingInfo> getDrawingInfos() {
        // 頂点座標の配列、法線の配列、色の配列を作る
        float[] vertices = new float[this.mNumVertices * 3];
        float[] normals = new float[this.mNumVertices * 3];
        float[] colors = new float[this.mNumVertices * 4];
        float[] uvs = new float[this.mNumVertices * 2];
        short[] indices = new short[this.mNumIndices];

        String texture = this.mMaterials.length > 0 ? this.mMaterials[0].texture : ""; // テクスチャは 1 番目のマテリアルに設定されているテクスチャのみ使用する。

        int index_vertices = 0, index_colors = 0, index_indices = 0, index_uvs = 0;
        for (int i = 0, nobj = this.mObjects.size(); i < nobj; i++) {
            MQOObject obj = this.mObjects.get(i);   // Objectごとの頂点情報を追加していく
            for (int j = 0, nface = obj.faces.length; j < nface; j++) {
                Face face = obj.faces[j];   // Faceごとの頂点情報を追加していく
                // Material情報の取得
                Material material;
                if (this.mMaterials != null && face.mIndex >= 0)
                    material = this.mMaterials[face.mIndex];
                else
                    material = obj.color;

                // 頂点インデックスごとに繰り返す
                int n = face.vIndices.length;
                for (int k = 0; k < n; k++) {
                    Vertex v = obj.vertices[face.vIndices[k]];

                    float[] normal;
                    if (obj.shading == 0)   // 面の法線か頂点の法線を用いるか
                        normal = face.normal; // 面の法線を使用
                    else
                        normal= v.normal;     // 頂点の法線を使用

                    for (int l = 0; l < 3; l++) {
                        vertices[index_vertices + k * 3 + l] = v.xyz[l]; // 頂点座標をverticesに設定
                        normals[index_vertices + k * 3 + l] = normal[l]; // 法線をnormalsに設定
                    }

                    for (int l = 0; l < 4; l++) // 色をcolorsに設定
                        colors[index_colors + k * 4 + l] = material.color[l];
                }

                for (int k = 0; k < face.uvs.length; k++) {
                    uvs[index_uvs + k] = face.uvs[k];
                }

                for (int l = 0; l < 3; l++) // インデックスをindices配列に設定
                    indices[index_indices + l] = (short)(index_vertices / 3 + l);

                if (n == 4) { // 四角形の場合はもう1つ三角形を
                    indices[index_indices + 3] = (short)(index_vertices / 3 + 0);
                    indices[index_indices + 4] = (short)(index_vertices / 3 + 2);
                    indices[index_indices + 5] = (short)(index_vertices / 3 + 3);
                    index_indices += 3;
                }
                index_indices += 3;
                index_vertices += (n * 3);
                index_colors += (n * 4);
                index_uvs += (n * 2);
            }
        }

        ArrayList<DrawingInfo> ret = new ArrayList<DrawingInfo>();
        ret.add(new DrawingInfo(vertices, normals, colors, uvs, indices, texture));

        /* ここに変更後のソースを追加 */
        {
            ArrayList<DrawingInfo> ret2 = new ArrayList<DrawingInfo>();
            for(int lpct = 0; lpct < mMaterials.length; lpct++) {
                DrawingInfo drawinginfo = new DrawingInfo();
                drawinginfo.texture = mMaterials[lpct].texture;
            }

            ArrayList<Float>[] vecVertices  = new ArrayList[ret2.size()];
            ArrayList<Float>[] vecNormals   = new ArrayList[ret2.size()];
            ArrayList<Float>[] vecColors    = new ArrayList[ret2.size()];
            ArrayList<Float>[] vecUvs       = new ArrayList[ret2.size()];
            ArrayList<Short>[] vecIndices   = new ArrayList[ret2.size()];
            for(int lpct = 0; lpct < ret2.size(); lpct++) {
                vecVertices[lpct]  = new ArrayList<Float>();
                vecNormals[lpct]   = new ArrayList<Float>();
                vecColors[lpct]    = new ArrayList<Float>();
                vecUvs[lpct]       = new ArrayList<Float>();
                vecIndices[lpct]   = new ArrayList<Short>();
            }

            int index_vertices2 = 0;
            for (int lpct = 0; lpct < mObjects.size(); lpct++) {
                MQOObject obj = mObjects.get(lpct);
                for (int j = 0; j < obj.faces.length; j++) {
                    Face face = obj.faces[j];
                    Material material;
                    if (this.mMaterials != null && face.mIndex >= 0)
                        material = this.mMaterials[face.mIndex];
                    else
                        material = obj.color;

                    for (int k = 0; k < face.vIndices.length; k++) {
                        Vertex v = obj.vertices[face.vIndices[k]];

                        float[] normal;
                        if (obj.shading == 0)   // 面の法線か頂点の法線を用いるか
                            normal = face.normal; // 面の法線を使用
                        else
                            normal= v.normal;     // 頂点の法線を使用

                        for (int l = 0; l < 3; l++) {
                            vecVertices[face.mIndex].add(v.xyz[l]);         // 頂点座標を追加
                            vecNormals[face.mIndex].add(normal[l]);// 法線をnormalsに設定
                        }

                        for (int l = 0; l < 4; l++) // 色をcolorsに設定
                            vecColors[face.mIndex].add(material.color[l]);
                    }

                    for (int k = 0; k < face.uvs.length; k++)
                        vecUvs[face.mIndex].add(face.uvs[k]);

                    for (int l = 0; l < 3; l++) // インデックスをindices配列に設定
                        vecIndices[face.mIndex].add((short)(index_vertices2 / 3 + l));

                    if(face.vIndices.length == 4) {
                        vecIndices[face.mIndex].add((short)(index_vertices2 / 3 + 0));
                        vecIndices[face.mIndex].add((short)(index_vertices2 / 3 + 2));
                        vecIndices[face.mIndex].add((short)(index_vertices2 / 3 + 3));
                        index_indices += 3;
                    }
                    index_indices += 3;
                }
            }

            for(int lpct = 0; lpct < ret2.size(); lpct++) {
                DrawingInfo drawingInfo = ret2.get(lpct);
                drawingInfo = new DrawingInfo(vecVertices[lpct], vecNormals[lpct], vecColors[lpct], vecUvs[lpct],vecIndices[lpct], drawingInfo.texture);
            }
            ret2.get(0);
//          return ret2;
        }

        return ret;
    }






    //------------------------------------------------------------------------------
    // Materialオブジェクト
    //------------------------------------------------------------------------------
    public class Material {

        public float[] color;
        public String texture;

        public Material(float r, float g, float b, float a) {
            this.color = new float[] {r, g, b, a};
            this.texture = null;
        }

    }

    //------------------------------------------------------------------------------
    // MQOObjectオブジェクト
    //------------------------------------------------------------------------------
    public class MQOObject {

        public int shading;
        public Material color;
        public Vertex[] vertices;
        public Face[] faces;

        public MQOObject() {
            this.shading = 1;
            this.color = null;
            this.vertices = null;
            this.faces = null;
        }

    }

    //------------------------------------------------------------------------------
    // Vertexオブジェクト
    //------------------------------------------------------------------------------
    public class Vertex {

        public float[] xyz;
        public float[] normal;

        public Vertex(float x, float y, float z) {
            this.xyz = new float[] {x, y, z};
            this.normal = new float[] {0.0f, 0.0f, 0.0f}; // この頂点を共有する全部の面の法線の合計
        }

    }

    //------------------------------------------------------------------------------
    // Faceオブジェクト
    //------------------------------------------------------------------------------
    public class Face {

        public short[] vIndices;
        public int mIndex;
        public float[] normal;
        public float[] uvs;

        public Face(short[] vIndices, int mIndex, float[] uvs) {
            this.vIndices = vIndices;
            this.mIndex = mIndex;
            this.normal = null; // この面の法線
            this.uvs = uvs;
        }

        // 法線の設定
        public void setNormal(Vertex[] vertices) {
            Vertex v0 = vertices[this.vIndices[0]];
            Vertex v1 = vertices[this.vIndices[1]];
            Vertex v2 = vertices[this.vIndices[2]];

            // 面の法線を計算してnormalに設定
            this.normal = calcNormal(v0.xyz, v1.xyz, v2.xyz);
            // 法線が正しく求められたか調べる
            if (this.normal == null) {
                if (this.vIndices.length == 4) { // 面が四角形なら別の3点の組み合わせで法線計算
                    Vertex v3 = vertices[this.vIndices[3]];
                    this.normal = calcNormal(v1.xyz, v2.xyz, v3.xyz);
                }
                if (this.normal == null) {         // 法線が求められなかったのでY軸方向の法線とする
                    this.normal = new float[] {0.0f, 1.0f, 0.0f};
                }
            }
            // この面を構成する点にも法線を追加しておく
            for (int i = 0, len = this.vIndices.length; i < len; i++) {
                Vertex v = vertices[this.vIndices[i]];
                v.normal[0] += this.normal[0]; // 加算していることに注意
                v.normal[1] += this.normal[1];
                v.normal[2] += this.normal[2];
            }
        }

    }

    //------------------------------------------------------------------------------
    // 描画情報オブジェクト(頂点座標配列、法線配列、色配列、インデックス配列)
    //------------------------------------------------------------------------------
    public class DrawingInfo {

        public float[] vertices;
        public float[] normals;
        public float[] colors;
        public float[] uvs;
        public short[] indices;
        public String texture;
        public Bitmap image;

        public DrawingInfo() {}
        public DrawingInfo(float[] vertices, float[] normals, float[] colors, float[] uvs, short[] indices, String texture) {
            this.vertices = vertices;
            this.normals = normals;
            this.colors = colors;
            this.uvs = uvs;
            this.indices = indices;
            this.texture = texture;
        }

    }

    //------------------------------------------------------------------------------
    // 文字列解析オブジェクト
    //------------------------------------------------------------------------------
    public class StringParser {

        public String str;
        public int index;

        // コンストラクタ
        public StringParser(String str) {
            this.init(str);
        }

        // StringParserオブジェクトを初期化する
        public void init(String str) {
            this.str = str;
            this.index = 0;
        }

        // 区切り文字を' ','\t','(',')', '"'としてすべて読み飛ばす
        public void skipDelimiters() {
            int i, len;
            for (i = this.index, len = this.str.length(); i < len; i++) {
                char c = this.str.charAt(i);
                // TAB、Space、'('、')'を読み飛ばす
                if (c == '\t'|| c == ' ' || c == '(' || c == ')' || c == '"') continue;
                break;
            }
            this.index = i;
        }

        // 次の単語の先頭まで読み飛ばす
        public void skipToNextWord() {
            this.skipDelimiters();
            int n = getWordLength(this.str, this.index);
            this.index += (n + 1);
        }

        // 単語を取得する
        public String getWord() {
            this.skipDelimiters();
            int n = getWordLength(this.str, this.index);
            if (n == 0) return null;
            String word = this.str.substring(this.index, this.index + n);
            this.index += (n + 1);

            return word;
        }

        // 整数を取得する
        public int getInt() {
            return Integer.parseInt(getWord()); // 文字列を整数に変換する
        }

        // 実数を取得する
        public float getFloat() {
            return Float.parseFloat(getWord()); // 文字列を浮動小数点に変換する
        }

        // 区切り文字を' ','\t','(',')', '"'として、区切りまでの文字数を数える
        public int getWordLength(String str, int start) {
            int i, len;
            for (i = start, len = str.length(); i < len; i++) {
                char c = str.charAt(i);
                if (c == '\t' || c == ' ' || c == '(' || c == ')' || c == '"')
                    break;
            }
            return i - start;
        }

    }

    //------------------------------------------------------------------------------
    // 共通関数(法線を求める)
    //------------------------------------------------------------------------------
    public static float[] calcNormal(float[] p0, float[] p1, float[] p2) {
        // p1からp0へのベクトル、p1からp2へのベクトルを求める
        float[] v0 = new float[3];
        float[] v1 = new float[3];
        for (int i = 0; i < 3; i++) {
            v0[i] = p0[i] - p1[i];
            v1[i] = p2[i] - p1[i];
        }

        // v0,v1の外積を求める
        float[] c = new float[3];
        c[0] = v0[1] * v1[2] - v0[2] * v1[1];
        c[1] = v0[2] * v1[0] - v0[0] * v1[2];
        c[2] = v0[0] * v1[1] - v0[1] * v1[0];

        // 正規化する
        Utils.normalizeVector3(c, 0);
        return c;
    }
}
