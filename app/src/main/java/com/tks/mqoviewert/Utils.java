package com.tks.mqoviewert;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Created by jun on 2016/11/29.
 */
public class Utils {
    private static final String TAG = "GLES20";

    /**
     * プログラムオブジェクトを生成し、WebGLシステムに設定する
     * @param vshader 頂点シェーダのプログラム(文字列)
     * @param fshader フラグメントシェーダのプログラム(文字列)
     * @return プログラムオブジェクト
     */
    public static int initShaders(String vshader, String fshader) {
        int program = createProgram(vshader, fshader);

        GLES20.glUseProgram(program);

        return program;
    }

    /**
     * リンク済みのプログラムオブジェクトを生成する
     * @param vshader 頂点シェーダのプログラム(文字列)
     * @param fshader フラグメントシェーダのプログラム(文字列)
     * @return 作成したプログラムオブジェクト
     */
    public static int createProgram(String vshader, String fshader) {
        // シェーダオブジェクトを作成する
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vshader);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fshader);

        // プログラムオブジェクトを作成する
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("failed to create program");
        }

        // シェーダオブジェクトを設定する
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        // プログラムオブジェクトをリンクする
        GLES20.glLinkProgram(program);

        // リンク結果をチェックする
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(program);
            throw new RuntimeException("failed to link program: " + error);
        }
        return program;
    }

    /**
     * シェーダオブジェクトを作成する
     * @param type 作成するシェーダタイプ
     * @param source シェーダのプログラム(文字列)
     * @return 作成したシェーダオブジェクト。
     */
    public static int loadShader(int type, String source) {
        // シェーダオブジェクトを作成する
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            throw new RuntimeException("unable to create shader");
        }

        // シェーダのプログラムを設定する
        GLES20.glShaderSource(shader, source);

        // シェーダをコンパイルする
        GLES20.glCompileShader(shader);

        // コンパイル結果を検査する
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetShaderInfoLog(shader);
            throw new RuntimeException("failed to compile shader: " + error);
        }

        return shader;
    }

    public static void normalizeVector3(float[] v, int offset) {
        float length = (float)Math.sqrt(v[offset] * v[offset] + v[offset + 1] * v[offset + 1] + v[offset + 2] * v[offset + 2]);
        if (length == 0) return;
        v[offset] /= length;
        v[offset + 1] /= length;
        v[offset + 2] /= length;
    }

    /**
     * 透視投影行列を作成する
     * @param m 透視投影行列
     * @param offset mのどこから計算した行列を格納するか
     * @param fovy 視野角
     * @param aspect 近平面の縦横比
     * @param zNear 近平面までの距離
     * @param zFar 遠平面までの距離
     * @return なし
     */
    public static void setPerspectiveM(float[] m, int offset, double fovy, double aspect, double zNear, double zFar) {
        Matrix.setIdentityM(m, offset);
        double ymax = zNear * Math.tan(fovy * Math.PI / 360.0);
        double ymin = -ymax;
        double xmin = ymin * aspect;
        double xmax = ymax * aspect;
        Matrix.frustumM(m, offset, (float)xmin, (float)xmax, (float)ymin, (float)ymax, (float)zNear, (float)zFar);
    }

    /**
     * 指定の byte 型配列をコピーしたダイレクトバッファを作成します。
     * @param array int 型の配列
     * @return IntBuffer 型のオブジェクトを返します。
     * @exception java.lang.IllegalArgumentException 引数に null が指定された場合に投げられます。
     */
    public static ShortBuffer makeShortBuffer(short[] array) {
        if (array == null) throw new IllegalArgumentException();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(2 * array.length);
        byteBuffer.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
        shortBuffer.put(array);
        shortBuffer.position(0);
        return shortBuffer;
    }

    /**
     * 指定の float 型配列をコピーしたダイレクトバッファを作成します。
     * @param array float 型の配列
     * @return FloatBuffer 型のオブジェクトを返します。
     * @exception java.lang.IllegalArgumentException 引数に null が指定された場合に投げられます。
     */
    public static FloatBuffer makeFloatBuffer(float[] array) {
        if (array == null) throw new IllegalArgumentException();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * array.length);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(array);
        floatBuffer.position(0);
        return floatBuffer;
    }

}
