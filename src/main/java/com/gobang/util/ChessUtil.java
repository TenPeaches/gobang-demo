package com.gobang.util;

/**
 * 五子棋判赢工具类：提供静态方法，判断落子后是否连成5子（赢棋）
 */
public class ChessUtil {
    // 判断落子后是否获胜（15x15棋盘）
    public static boolean isWin(int[][] chessboard, int x, int y, int player) {
        int size = chessboard.length;
        
        // 1. 横向检查（以(x,y)为中心，向左向右检查）
        int count = 1; // 当前落子点本身算1个
        // 向左检查
        for (int i = y - 1; i >= 0 && chessboard[x][i] == player; i--) {
            count++;
        }
        // 向右检查
        for (int i = y + 1; i < size && chessboard[x][i] == player; i++) {
            count++;
        }
        if (count >= 5) return true;
        
        // 2. 纵向检查（以(x,y)为中心，向上向下检查）
        count = 1;
        // 向上检查
        for (int i = x - 1; i >= 0 && chessboard[i][y] == player; i--) {
            count++;
        }
        // 向下检查
        for (int i = x + 1; i < size && chessboard[i][y] == player; i++) {
            count++;
        }
        if (count >= 5) return true;
        
        // 3. 正对角线（左上→右下，以(x,y)为中心，向左上向右下检查）
        count = 1;
        // 向左上检查
        int i = x - 1, j = y - 1;
        while (i >= 0 && j >= 0 && chessboard[i][j] == player) {
            count++;
            i--; j--;
        }
        // 向右下检查
        i = x + 1; j = y + 1;
        while (i < size && j < size && chessboard[i][j] == player) {
            count++;
            i++; j++;
        }
        if (count >= 5) return true;
        
        // 4. 反对角线（右上→左下，以(x,y)为中心，向右上向左下检查）
        count = 1;
        // 向右上检查
        i = x - 1; j = y + 1;
        while (i >= 0 && j < size && chessboard[i][j] == player) {
            count++;
            i--; j++;
        }
        // 向左下检查
        i = x + 1; j = y - 1;
        while (i < size && j >= 0 && chessboard[i][j] == player) {
            count++;
            i++; j--;
        }
        if (count >= 5) return true;
        
        return false;
    }
}