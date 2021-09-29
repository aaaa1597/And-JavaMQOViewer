package com.tks.mqoviewert;

import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MQOViewerTActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    final int FSIZE = Float.SIZE / Byte.SIZE; // floatのバイト数

    // 頂点シェーダのプログラム
    private static final String VSHADER_SOURCE =
        "attribute vec4 a_Position;\n" +
        "attribute vec4 a_Color;\n" +
        "attribute vec4 a_Normal;\n" +
        "uniform mat4 u_MvpMatrix;\n" +
        "uniform mat4 u_NormalMatrix;\n" +
        "varying vec4 v_Color;\n" +
        "void main() {\n" +
        "  vec3 lightDirection = vec3(-0.35, 0.35, 0.87);\n" +
        "  gl_Position = u_MvpMatrix * a_Position;\n" +
        "  vec3 normal = normalize(vec3(u_NormalMatrix * a_Normal));\n" +
        "  float nDotL = max(dot(normal, lightDirection), 0.0);\n" +
        "  v_Color = vec4(a_Color.rgb * nDotL, a_Color.a);\n" +
        "}\n";

    // フラグメントシェーダのプログラム
    private static final String FSHADER_SOURCE =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec4 v_Color;\n" +
        "void main() {\n" +
        "  gl_FragColor = v_Color;\n" +
        "}\n";

    // 頂点シェーダのプログラム
    private static final String TEX_VSHADER_SOURCE =
        "attribute vec4 a_Position;\n" +
        "attribute vec4 a_Color;\n" +
        "attribute vec4 a_Normal;\n" +
        "attribute vec2 a_TexCoord;\n" +
        "uniform mat4 u_MvpMatrix;\n" +
        "uniform mat4 u_NormalMatrix;\n" +
        "varying vec4 v_Color;\n" +
        "varying vec2 v_TexCoord;\n" +
        "void main() {\n" +
        "  vec3 lightDirection = vec3(-0.35, 0.35, 0.87);\n" +
        "  gl_Position = u_MvpMatrix * a_Position;\n" +
        "  vec3 normal = normalize(vec3(u_NormalMatrix * a_Normal));\n" +
        "  float nDotL = max(dot(normal, lightDirection), 0.0);\n" +
        "  v_Color = vec4(a_Color.rgb * nDotL, a_Color.a);\n" +
        "  v_TexCoord = a_TexCoord;\n" +
        "}\n";

    // フラグメントシェーダのプログラム
    private static final String TEX_FSHADER_SOURCE =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "uniform sampler2D u_Sampler;\n" +
        "varying vec4 v_Color;\n" +
        "varying vec2 v_TexCoord;\n" +
        "void main() {\n" +
        "  gl_FragColor = v_Color * texture2D(u_Sampler, v_TexCoord);\n" +
        "}\n";

    private static float ANGLE_STEP = 30;   // 回転角の増分(度)

    // メンバー変数
    private GLSurfaceView mGLSurfaceView; // 描画領域
    private GLShader mGLShader;
    private GLShader mTexGLShader;

    // 座標変換行列
    private float[] mModelMatrix = new float[16];
    private float[] mViewProjMatrix = new float[16];
    private float[] mMvpMatrix = new float[16];
    private float[] mNormalMatrix = new float[16];

    // モデル描画情報
    private GLBuff mGLBuff;
    private ArrayList<MQODoc.DrawingInfo> mDrawingInfos = new ArrayList<MQODoc.DrawingInfo>();

    private float mCurrentAngle;
    private long mLast; // 最後に呼び出された時刻

    public class GLShader {
        public int program;
        public int a_Position;
        public int a_Normal;
        public int a_Color;
        public int a_TexCoord;
        public int u_MvpMatrix;
        public int u_NormalMatrix;
        public int u_Sampler;

        public GLShader(int program) {
            this.program = program;
            this.a_Position = -1;
            this.a_Normal = -1;
            this.a_Color = -1;
            this.a_TexCoord = -1;
            this.u_MvpMatrix = -1;
            this.u_NormalMatrix = -1;
            this.u_Sampler = -1;
        }
    }

    public class GLBuff {
        public int vertexBuffId;
        public int normalBuffId;
        public int colorBuffId;
        public int uvBuffId;
        public int indexBuffId;
        public int texId;

        public GLBuff() {
            vertexBuffId = 0;
            normalBuffId = 0;
            colorBuffId = 0;
            uvBuffId = 0;
            indexBuffId = 0;
            texId = 0;
        }
    }

    private float mLastX,mLastY;
    private float[] mTouchAngle = {0f,0f};
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = event.getX();
                mLastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                {
                float factor = 100f / mHeight;
                float dx = factor * (event.getX() - mLastX);
                float dy = factor * (event.getY() - mLastY);
                mTouchAngle[0] = Math.max(Math.min(mTouchAngle[0]+dy,90f),-90f);
                mTouchAngle[1] += dx;
                mLastX = event.getX();
                mLastY = event.getY();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    int mSelectedItem = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(this);
        setContentView(mGLSurfaceView);

        /* スピナー生成 */
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.add("sakana.mqo");
        adapter.add("vase.mqo");
        adapter.add("vignette_ppp.mqo");
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mLoadStatus = 0;
                mSelectedItem = position;
                final String item = (String)((Spinner)parent).getSelectedItem();
                mGLSurfaceView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        readFile(item);
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        addContentView(spinner, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mGLShader = new GLShader(Utils.initShaders(VSHADER_SOURCE, FSHADER_SOURCE));     // シェーダを初期化する
        mGLShader.a_Position    = GLES20.glGetAttribLocation(mGLShader.program, "a_Position");
        mGLShader.a_Normal      = GLES20.glGetAttribLocation(mGLShader.program, "a_Normal");
        mGLShader.a_Color       = GLES20.glGetAttribLocation(mGLShader.program, "a_Color");
        mGLShader.a_TexCoord    = -1;
        mGLShader.u_MvpMatrix   = GLES20.glGetUniformLocation(mGLShader.program, "u_MvpMatrix");
        mGLShader.u_NormalMatrix= GLES20.glGetUniformLocation(mGLShader.program, "u_NormalMatrix");
        mGLShader.u_Sampler     = -1;
        if (mGLShader.a_Position == -1 || mGLShader.a_Normal == -1 || mGLShader.a_Color == -1 ||
            mGLShader.u_MvpMatrix == -1 || mGLShader.u_NormalMatrix == -1) {
            throw new RuntimeException("attribute, uniform変数の格納場所の取得に失敗");
        }

        mTexGLShader = new GLShader(Utils.initShaders(TEX_VSHADER_SOURCE, TEX_FSHADER_SOURCE));     // シェーダを初期化する
        mTexGLShader.a_Position     = GLES20.glGetAttribLocation(mTexGLShader.program, "a_Position");
        mTexGLShader.a_Normal       = GLES20.glGetAttribLocation(mTexGLShader.program, "a_Normal");
        mTexGLShader.a_Color        = GLES20.glGetAttribLocation(mTexGLShader.program, "a_Color");
        mTexGLShader.a_TexCoord     = GLES20.glGetAttribLocation(mTexGLShader.program, "a_TexCoord");
        mTexGLShader.u_MvpMatrix    = GLES20.glGetUniformLocation(mTexGLShader.program, "u_MvpMatrix");
        mTexGLShader.u_NormalMatrix = GLES20.glGetUniformLocation(mTexGLShader.program, "u_NormalMatrix");
        mTexGLShader.u_Sampler      = GLES20.glGetUniformLocation(mTexGLShader.program, "u_Sampler");
        if (mTexGLShader.a_Position == -1 || mTexGLShader.a_Normal == -1 || mTexGLShader.a_Color == -1 || mTexGLShader.a_TexCoord == -1 ||
            mTexGLShader.u_MvpMatrix== -1 || mTexGLShader.u_NormalMatrix == -1 || mTexGLShader.u_Sampler == -1) {
            throw new RuntimeException("attribute, uniform変数の格納場所の取得に失敗");
        }

        // クリアカラーを設定し、デプステストを有効にする
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // 頂点座標と色、法線用の空のバッファオブジェクトを用意する
        mGLBuff = new GLBuff();
        int[] buffer = new int[5];
        GLES20.glGenBuffers(5, buffer, 0);
        mGLBuff.vertexBuffId  = buffer[0];
        mGLBuff.normalBuffId  = buffer[1];
        mGLBuff.colorBuffId   = buffer[2];
        mGLBuff.uvBuffId      = buffer[3];
        mGLBuff.indexBuffId   = buffer[4];

        // テクスチャオブジェクトを作成する
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        mGLBuff.texId = texture[0];

        mCurrentAngle = 0.0f; // 現在の回転角 [degree]

        mLast = SystemClock.uptimeMillis();
    }

    int mHeight;
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mHeight = height;
        GLES20.glViewport(0, 0, width, height);     // 表示領域を設定する

        // ビュー投影行列を計算
        float[] projMatrix = new float[16];
        float[] viewMatrix = new float[16];
        Utils.setPerspectiveM(projMatrix, 0, 30.0, (double)width / (double)height, 1.0, 5000.0);
        Matrix.setLookAtM(viewMatrix, 0, 0.0f, 250.0f, 1000.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix.multiplyMM(mViewProjMatrix, 0, projMatrix, 0, viewMatrix, 0);
    }

    int mLoadStatus = 0;
    @Override
    public void onDrawFrame(GL10 gl) {
        if(mLoadStatus == 0) return;

        mCurrentAngle = animate(mCurrentAngle); // 回転角度を更新する
        for(int lpct = 0; lpct < mDrawingInfos.size(); lpct++) {
            MQODoc.DrawingInfo drawinginfo = mDrawingInfos.get(lpct);

            GLShader shader = drawinginfo.texture != null ? mTexGLShader : mGLShader;
            GLES20.glUseProgram(shader.program);
            GLES20.glUniform1i(shader.u_Sampler, 0);

            // 頂点
            FloatBuffer vertexs = Utils.makeFloatBuffer(drawinginfo.vertices);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLBuff.vertexBuffId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, FSIZE * vertexs.limit(), vertexs, GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(shader.a_Position, 3, GLES20.GL_FLOAT, false, 0, 0);  // attribute変数にバッファオブジェクトを割り当てる
            GLES20.glEnableVertexAttribArray(shader.a_Position);  // 割り当てを有効にする

            // 法線
            FloatBuffer normals = Utils.makeFloatBuffer(drawinginfo.normals);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLBuff.normalBuffId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, FSIZE * normals.limit(), normals, GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(shader.a_Normal, 3, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glEnableVertexAttribArray(shader.a_Normal);

            // カラー
            FloatBuffer colors = Utils.makeFloatBuffer(drawinginfo.colors);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLBuff.colorBuffId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, FSIZE * colors.limit(), colors, GLES20.GL_STATIC_DRAW);
            GLES20.glVertexAttribPointer(shader.a_Color, 4, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glEnableVertexAttribArray(shader.a_Color);

            if (drawinginfo.texture != null) {
                /* UV設定 */
                FloatBuffer uvs = Utils.makeFloatBuffer(drawinginfo.uvs);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLBuff.uvBuffId);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, FSIZE * uvs.limit(), uvs, GLES20.GL_STATIC_DRAW);
                GLES20.glVertexAttribPointer(shader.a_TexCoord, 2, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glEnableVertexAttribArray(shader.a_TexCoord);
                /* テクスチャ画像設定 */
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGLBuff.texId);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, drawinginfo.image, 0);
            }

            // インデックスをバッファオブジェクトに書き込む
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mGLBuff.indexBuffId);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * drawinginfo.indices.length, Utils.makeShortBuffer(drawinginfo.indices), GLES20.GL_STATIC_DRAW);

            calcCordinate(shader, mCurrentAngle, mViewProjMatrix);
            // 描画
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawinginfo.indices.length, GLES20.GL_UNSIGNED_SHORT, 0);
        }
    }

    private void readFile(String fileName) {
        String fileString = "";
        try {
            fileString = toStringFromAssetFile(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        onRead(fileString);
    }

    // MQOファイルが読み込まれた
    private void onRead(String fileString) {
        MQODoc mqoDoc = new MQODoc();  // MQODocオブジェクトの作成
        boolean result = mqoDoc.parse(fileString); // ファイルの解析
        if (!result) {
            mDrawingInfos.clear();
            return;
        }

        // MQOファイル内の頂点座標、法線、色情報の取得
        mDrawingInfos = mqoDoc.getDrawingInfos();

        // プログラム切り替える際に色々設定解除した方が良いでんすかねぇ...

        boolean isTexFlg = false;
        for(int lpct = 0; lpct < mDrawingInfos.size(); lpct++) {
            MQODoc.DrawingInfo drawinginfo = mDrawingInfos.get(lpct);
            if(drawinginfo.texture == null) continue;
            // 画像オブジェクトを作成する
            try {
                drawinginfo.image = BitmapFactory.decodeStream(getAssets().open(drawinginfo.texture));
            } catch (IOException e) {
                throw new RuntimeException();
            }
            isTexFlg = true;
        }
        if(isTexFlg) {
            // テクスチャユニット0を有効にする
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // テクスチャオブジェクトをバインドする
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGLBuff.texId);
            // テクスチャパラメータを設定する
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        }

        mLoadStatus = 1;
    }

    // 描画関数
    private void calcCordinate(GLShader program, float angle, float[] viewProjMatrix) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);  // カラーバッファとデプスバッファをクリア

        if(mSelectedItem == 2) {
            Matrix.setRotateM(mModelMatrix, 0, mTouchAngle[0], 1.0f, 0.0f, 0.0f); // 適当に回転
            Matrix.rotateM(mModelMatrix, 0, mTouchAngle[1], 0.0f, 1.0f, 0.0f);
            Matrix.translateM(mModelMatrix, 0, 0.0f, -150.0f, 0.0f);
            Matrix.scaleM(mModelMatrix, 0, 0.9f, 0.9f, 0.9f);
        }
        else {
            Matrix.setRotateM(mModelMatrix, 0, angle, 1.0f, 0.0f, 0.0f); // 適当に回転
            Matrix.rotateM(mModelMatrix, 0, angle, 0.0f, 1.0f, 0.0f);
            Matrix.rotateM(mModelMatrix, 0, angle, 0.0f, 0.0f, 1.0f);
        }

        // 法線の変換行列を計算し、u_NormalMatrixに設定する
        float[] inv = new float[16];
        Matrix.invertM(inv, 0, mModelMatrix, 0);
        Matrix.transposeM(mNormalMatrix, 0, inv, 0);
        GLES20.glUniformMatrix4fv(program.u_NormalMatrix, 1, false, mNormalMatrix, 0);

        // モデルビュー投影行列を計算し、u_MvpMatrixに設定する
        Matrix.multiplyMM(mMvpMatrix, 0, viewProjMatrix, 0, mModelMatrix, 0);
        GLES20.glUniformMatrix4fv(program.u_MvpMatrix, 1, false, mMvpMatrix, 0);
    }

    private float animate(float angle) {
        long now = SystemClock.uptimeMillis();   // 前回呼び出されてからの経過時間を計算
        long elapsed = now - mLast;
        mLast = now;
        // 回転角度を更新する(経過時間により調整)
        float newAngle = angle + (ANGLE_STEP * elapsed) / 1000.0f;
        return newAngle %= 360;
    }

    private String toStringFromAssetFile(String fileName) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        InputStream inputStream = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        try {
            inputStream = getAssets().open(fileName);
            inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            bufferedReader = new BufferedReader(inputStreamReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) bufferedReader.close();
            if (inputStreamReader != null) inputStreamReader.close();
            if (inputStream != null) inputStream.close();
        }
        return stringBuilder.toString();
    }
}
