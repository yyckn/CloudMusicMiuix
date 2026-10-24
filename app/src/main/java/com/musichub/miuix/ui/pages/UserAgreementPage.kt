package com.musichub.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 用户协议页：首次使用前展示。
 * 同意前屏蔽返回，勾选复选框后才允许点击底部“开始使用”。
 */
@Composable
fun UserAgreementPage(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 屏蔽返回键与返回手势
    BackHandler(enabled = true) { /* 不处理，阻止退出 */ }

    var agreed by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(title = "用户协议")
        },
        bottomBar = {
            Surface(color = MiuixTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = agreed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (agreed) "开始使用" else "请阅读用户协议")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = AGREEMENT_TITLE,
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = AGREEMENT_META,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = AGREEMENT_WARNING,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )

                        AGREEMENT_SECTIONS.forEach { section ->
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = section.title,
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = section.body,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    CheckboxPreference(
                        title = "我已认真阅读并同意以上所有用户协议。",
                        checked = agreed,
                        onCheckedChange = { agreed = it }
                    )
                }
            }
        }
    }
}

private data class AgreementSection(val title: String, val body: String)

private const val AGREEMENT_TITLE = "MusicHub 用户协议与免责声明"

private const val AGREEMENT_META = "应用包名： com.musichub.miuix\n版本日期： 2026年10月24日"

private const val AGREEMENT_WARNING =
    "特别提示：请在使用本软件前，完整阅读本协议。当你点击“同意”或实际使用本软件，即视为你已阅读并接受本协议全部内容。如果你不同意，请立即停止使用并卸载本软件。"

private val AGREEMENT_SECTIONS = listOf(
    AgreementSection(
        title = "一、协议主体与软件性质",
        body = """
1.1 本软件（包名 com.musichub.miuix，以下简称“MusicHub”）是由个人开发者（以下简称“开发者”）编写、仅供个人非商业用途的第三方音乐客户端工具。

1.2 MusicHub 不是网易云音乐、酷狗音乐或其关联公司的官方产品，与上述平台不存在隶属、合作、授权或背书关系。“网易云音乐”、“酷狗音乐”等名称、商标、标识归各自权利人所有；本软件中使用相关名称仅用于客观描述所对接的技术接口，不构成对任何官方关系的暗示。

1.3 MusicHub 的定位是客户端工具：它本身不生产、不存储、不拥有、不分发任何音乐音频、歌词、封面或用户数据。软件所展示或播放的内容，均来自你所登录/配置的第三方平台或本地文件。
""".trimIndent()
    ),
    AgreementSection(
        title = "二、使用许可与限制",
        body = """
2.1 开发者授予你一项个人的、非独占的、不可转让的、可撤销的许可，允许你在自己的设备上安装和使用 MusicHub，仅限个人学习、研究和日常使用。

2.2 你不得：

将 MusicHub 用于任何商业目的，包括但不限于销售、出租、有偿分发、捆绑推广或以其他形式获取经济利益；

对 MusicHub 进行反向工程、反编译、反汇编，或试图获取其源代码（适用开源许可证另有约定的除外）；

删除、遮挡或修改 MusicHub 中的版权声明、免责声明或权利标识；

将 MusicHub 重新打包、修改后以“官方版本”或“授权版本”名义对外分发。
""".trimIndent()
    ),
    AgreementSection(
        title = "三、第三方平台账号与风控风险",
        body = """
3.1 使用 MusicHub 登录或连接网易云音乐、酷狗音乐等第三方平台，可能违反该平台的服务条款。第三方平台有权独立判断你的账号行为是否“异常”，并可能采取强制下线、要求重置密码、临时冻结账号等措施。已有公开案例显示，使用非官方客户端登录网易云音乐可能导致账号被临时冻结（通常为数天），具体时长和判定标准由平台单方决定。

3.2 你理解并同意：是否使用 MusicHub 登录第三方平台账号，完全由你自行评估和决定。你应自行承担因使用第三方平台服务而产生的全部后果，包括但不限于账号被限制、冻结、封禁，会员权益受限，或与平台之间的任何争议。开发者不对上述后果承担责任，也无法代表你向任何平台进行申诉或恢复账号。

3.3 MusicHub 不要求你向开发者提供网易云音乐或酷狗音乐的密码。如果你在软件内完成登录，登录凭据仅用于与第三方平台之间完成身份验证，开发者不收集、不存储你的平台密码。但请注意：第三方平台的风控系统可能识别到登录请求来自非官方客户端，这与你是否向开发者提交密码无关。
""".trimIndent()
    ),
    AgreementSection(
        title = "四、版权声明",
        body = """
4.1 通过 MusicHub 访问、播放、下载或以其他方式接触到的所有音乐、音频流、歌词、封面、艺人信息及其他内容，其版权及相关权利均归原权利人所有。MusicHub 不主张对上述内容的任何所有权。

4.2 MusicHub 不提供任何绕过版权保护的技术手段，也不以“破解”“免费获取付费内容”为功能目标。你所接触到的内容是否可供你访问、播放或使用，取决于你与相应平台之间的授权关系（例如你是否拥有有效的会员资格、该内容在你的所在地区是否可用等）。

4.3 你应自行确保你对通过 MusicHub 接触到的内容的使用方式不侵犯他人的著作权、商标权或其他权利。因你的使用行为引发的版权纠纷、平台投诉或法律责任，由你自行承担。

4.4 如果你认为 MusicHub 本身（而非第三方平台内容）侵犯了你的合法权益，可通过本协议末尾的联系方式告知开发者，开发者将在合理时间内处理。
""".trimIndent()
    ),
    AgreementSection(
        title = "五、免责声明",
        body = """
5.1 MusicHub 按“现状”和“现有”基础提供。开发者在法律允许的最大范围内，不对软件作出任何明示或默示的保证，包括但不限于：软件无错误、不中断、无病毒或有害组件、适销性、特定用途适用性，以及通过本软件获取的内容的准确性、合法性或可用性。

5.2 第三方平台的接口、页面结构、鉴权机制、风控策略可能随时变更。因第三方平台单方变更接口、中断服务、限制访问或改变风控规则，导致 MusicHub 的部分或全部功能不可用、账号出现异常或数据丢失的，开发者不承担赔偿责任。

5.3 你理解并同意：使用 MusicHub 所产生的风险由你自行承担。在法律允许的范围内，开发者不对因使用或无法使用本软件而产生的任何间接损失、附带损失、惩罚性损失或利润损失承担责任。

5.4 本条不排除或限制依法不可排除或限制的责任。
""".trimIndent()
    ),
    AgreementSection(
        title = "六、隐私与数据",
        body = """
6.1 MusicHub 不包含由开发者控制的遥测、行为分析或广告追踪功能，开发者不会主动将你的个人身份信息上传至自己控制的服务器。

6.2 你在 MusicHub 中的本地配置、播放历史、缓存文件等数据，原则上存储在你的设备上。当你使用第三方平台功能时，相关网络请求会直接与第三方平台进行，并受该平台隐私政策约束。

6.3 由于本软件目前面向朋友间个人使用，开发者未单独设立数据保护官或数据处理流程。如后续功能发生变化涉及数据收集，开发者将在软件更新中显著提示。
""".trimIndent()
    ),
    AgreementSection(
        title = "七、协议的变更与终止",
        body = """
7.1 开发者有权根据软件功能调整或法律要求修订本协议。修订后的协议将在应用内以适当方式展示；你在协议变更后继续使用 MusicHub，即视为接受修订后的内容。

7.2 如果你不同意本协议或其后续修订，应停止使用并卸载 MusicHub。你可以随时删除本软件以终止本协议。
""".trimIndent()
    ),
    AgreementSection(
        title = "八、法律适用与争议解决",
        body = """
8.1 本协议的订立、效力、解释及争议解决，适用中华人民共和国大陆地区法律（不含冲突法规则）。

8.2 因本协议或 MusicHub 的使用产生的争议，双方应首先友好协商解决。协商不成的，任何一方可向开发者住所地有管辖权的人民法院提起诉讼。作为个人非商业软件，开发者不具备通过仲裁或境外司法程序应对争议的能力，敬请理解。
""".trimIndent()
    ),
    AgreementSection(
        title = "九、其他",
        body = """
9.1 本协议任一条款被认定为无效或不可执行，不影响其余条款的效力。

9.2 本协议标题仅为方便阅读而设，不影响条款的解释。

9.3 如你对本协议有疑问，可通过你获取 MusicHub 时开发者提供的联系方式进行沟通。
""".trimIndent()
    )
)
