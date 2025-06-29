#include "opengl/OpenGLSlot.h"
#include "dragonBones/model/TextureAtlasData.h"
#include "dragonBones/armature/Armature.h"
#include "opengl/OpenGLFactory.h"
#include "dragonBones/model/DragonBonesData.h"
#include "dragonBones/model/DisplayData.h"
#include "dragonBones/armature/DeformVertices.h"

DRAGONBONES_NAMESPACE_BEGIN

namespace opengl
{

void OpenGLSlot::_onClear()
{
    Slot::_onClear();
    vertices.clear();
    indices.clear();
    textureID = 0;
}

void OpenGLSlot::_initDisplay(void* value, bool isRetain) {}
void OpenGLSlot::_disposeDisplay(void* value, bool isRetain) {}
void OpenGLSlot::_onUpdateDisplay() 
{
    _updateMesh();
}
void OpenGLSlot::_addDisplay() {}
void OpenGLSlot::_replaceDisplay(void* value, bool isRetain) {}
void OpenGLSlot::_removeDisplay() {}
void OpenGLSlot::_updateTransform() {}
void OpenGLSlot::_updateZOrder() {}

// Pure virtual methods that must be implemented
void OpenGLSlot::_updateVisible() {}
void OpenGLSlot::_updateBlendMode() {}
void OpenGLSlot::_updateColor() {}

// 新增方法的实现
void OpenGLSlot::_updateFrame()
{
    const auto* textureData = static_cast<const opengl::OpenGLTextureData*>(_textureData);
    if (!textureData) {
        vertices.clear();
        indices.clear();
        textureID = 0;
        return;
    }

    const auto* atlasData = static_cast<const opengl::OpenGLTextureAtlasData*>(textureData->parent);
    textureID = atlasData->textureID;

    // x, y, u, v
    vertices.resize(4 * 4); 
    const auto& region = textureData->region;
    const auto atlasWidth = atlasData->width;
    const auto atlasHeight = atlasData->height;

    float u_min = region.x / atlasWidth;
    float v_min = region.y / atlasHeight;
    float u_max = (region.x + region.width) / atlasWidth;
    float v_max = (region.y + region.height) / atlasHeight;

    float w = region.width;
    float h = region.height;

    // Vertex positions
    vertices[0] = -w / 2.0f; vertices[1] = -h / 2.0f;  // Top-left
    vertices[4] = w / 2.0f;  vertices[5] = -h / 2.0f;  // Top-right
    vertices[8] = -w / 2.0f; vertices[9] = h / 2.0f;   // Bottom-left
    vertices[12] = w / 2.0f; vertices[13] = h / 2.0f;  // Bottom-right

    // UV coordinates
    vertices[2] = u_min; vertices[3] = v_min;
    vertices[6] = u_max; vertices[7] = v_min;
    vertices[10] = u_min; vertices[11] = v_max;
    vertices[14] = u_max; vertices[15] = v_max;

    indices = {0, 1, 2, 1, 3, 2};
}

void OpenGLSlot::_updateMesh()
{
    const auto textureData = static_cast<const opengl::OpenGLTextureData*>(_textureData);
    if (!textureData)
    {
        vertices.clear();
        indices.clear();
        textureID = 0;
        return;
    }

    // 检查 _displayData 是否为 MeshDisplayData 类型
    if (_displayData == nullptr || _displayData->type != DisplayType::Mesh)
    {
        _updateFrame(); // 如果不是网格类型，回退到普通帧更新
        return;
    }

    const auto meshData = static_cast<const MeshDisplayData*>(_displayData);
    const auto& verticesData = meshData->vertices;
    const auto dragonBonesData = verticesData.data;

    if (!dragonBonesData)
    {
        return;
    }

    const auto intArray = dragonBonesData->intArray;
    const auto floatArray = dragonBonesData->floatArray;

    const auto vertexCount = intArray[verticesData.offset + (int)BinaryOffset::MeshVertexCount];
    const auto triangleCount = intArray[verticesData.offset + (int)BinaryOffset::MeshTriangleCount];
    int vertexOffset = intArray[verticesData.offset + (int)BinaryOffset::MeshFloatOffset];
    const auto indexOffset = intArray[verticesData.offset + (int)BinaryOffset::MeshVertexIndices];

    indices.resize(triangleCount * 3);
    const int16_t* meshIndices = &(intArray[indexOffset]);
    for (int i = 0; i < indices.size(); ++i) {
        indices[i] = meshIndices[i];
    }

    vertices.resize(vertexCount * 4); // each vertex has 4 floats (x, y, u, v)
    const auto& deformVertices = _deformVertices->vertices;
    const bool hasDeform = !deformVertices.empty();
    
    int uvOffset = vertexOffset + vertexCount * 2;

    for (int i = 0; i < vertexCount; ++i)
    {
        const float x = hasDeform ? deformVertices[i * 2] : floatArray[vertexOffset + i * 2];
        const float y = hasDeform ? deformVertices[i * 2 + 1] : floatArray[vertexOffset + i * 2 + 1];
        const float u = floatArray[uvOffset + i * 2];
        const float v = floatArray[uvOffset + i * 2 + 1];

        vertices[i * 4 + 0] = x;
        vertices[i * 4 + 1] = y;
        vertices[i * 4 + 2] = u;
        vertices[i * 4 + 3] = v;
    }

    // Update textureID
    const auto atlasData = static_cast<const opengl::OpenGLTextureAtlasData*>(textureData->parent);
    textureID = atlasData->textureID;
}

void OpenGLSlot::_identityTransform() {}

}  // namespace opengl

DRAGONBONES_NAMESPACE_END 