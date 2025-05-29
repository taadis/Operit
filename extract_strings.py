import xml.etree.ElementTree as ET
import os

def extract_strings_from_xml(xml_file_path, output_file_path=None):
    # 解析XML文件
    tree = ET.parse(xml_file_path)
    root = tree.getroot()
    
    # 提取所有string元素
    strings = {}
    for string_elem in root.findall('.//string'):
        name = string_elem.get('name')
        value = string_elem.text
        strings[name] = value
    
    # 输出到文件或返回字典
    if output_file_path:
        with open(output_file_path, 'w', encoding='utf-8') as f:
            for name, value in strings.items():
                f.write(f"{name}: {value}\n")
        print(f"已提取 {len(strings)} 个字符串到文件: {output_file_path}")
    
    return strings

if __name__ == "__main__":
    # 设置文件路径
    strings_xml_path = "app/src/main/res/values/strings.xml"
    output_path = "extracted_strings.txt"
    
    if not os.path.exists(strings_xml_path):
        print(f"错误: 找不到文件 {strings_xml_path}")
    else:
        extract_strings_from_xml(strings_xml_path, output_path) 