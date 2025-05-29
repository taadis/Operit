import re
import os

def extract_text_from_kotlin(kotlin_file_path, output_file_path=None):
    # 读取Kotlin文件
    with open(kotlin_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 定义多个正则表达式模式来匹配不同情况下的文本
    patterns = [
        r'Text\(\s*"([^"]*)"', # 匹配 Text("文本内容")
        r'Text\(\s*text\s*=\s*"([^"]*)"', # 匹配 Text(text = "文本内容")
        r'String\(\s*"([^"]*)"', # 匹配 String("文本内容")
        r'"([^"]+)"(?=\s*\))', # 匹配函数调用中的字符串参数
    ]
    
    extracted_texts = set()
    for pattern in patterns:
        matches = re.findall(pattern, content)
        extracted_texts.update(matches)
    
    # 过滤掉空白文本和重复项
    filtered_texts = [text for text in extracted_texts if text.strip()]
    
    # 输出到文件或返回结果
    if output_file_path:
        with open(output_file_path, 'w', encoding='utf-8') as f:
            for text in sorted(filtered_texts):
                f.write(f"{text}\n")
        print(f"已提取 {len(filtered_texts)} 个文本到文件: {output_file_path}")
    
    return filtered_texts

if __name__ == "__main__":
    # 设置文件路径
    kotlin_file_path = "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/uidebugger/UIDebuggerComponents.kt"
    output_path = "extracted_kotlin_texts.txt"
    
    if not os.path.exists(kotlin_file_path):
        print(f"错误: 找不到文件 {kotlin_file_path}")
    else:
        extract_text_from_kotlin(kotlin_file_path, output_path) 