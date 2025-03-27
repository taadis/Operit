/* METADATA
{
  // This is a sample HJSON package file
  // HJSON supports comments and is more human-friendly
  
  name: daily_life
  description: Tools for daily life activities like checking dates
  
  // Tools in this package
  tools: [
    {
      name: check_date
      description: Get the current date and time
      
      // Parameters can be empty for this tool
      parameters: []
    },
    {
      name: debug_js
      description: Helps diagnose JavaScript issues by evaluating code segments and reporting errors
      
      parameters: [
        {
          name: code
          description: JavaScript code to evaluate
          type: string
          required: true
        }
      ]
    }
  ]
  
  // Tool category
  category: SYSTEM_OPERATION
}
*/

exports.check_date = function() {
    try {
        // 使用一个更简单的同步实现方式
        console.log("Starting check_date function");
        
        // 获取当前日期和时间
        const timestamp = new Date();
        const formattedDate = dataUtils.formatDate(timestamp);
        
        // 返回日期信息
        const result = {
            timestamp: timestamp.getTime(),
            formatted: formattedDate,
            date: timestamp.toDateString(),
            time: timestamp.toTimeString()
        };
        
        console.log("Completing check_date function with result:", result);
        complete(result);
    } catch (error) {
        console.error("Error in check_date:", error);
        complete({ error: error.message });
    }
};

exports.debug_js = function(params) {
    try {
        console.log("Starting debug_js function");
        
        // Get the code to evaluate
        const code = params.code || "";
        
        if (!code) {
            complete({
                success: false,
                error: "No code provided to evaluate"
            });
            return;
        }
        
        console.log("Attempting to evaluate code:", code);
        
        let result;
        try {
            // Try to evaluate the code
            result = eval("(function() { try { " + code + " } catch (e) { return { error: e.message, stack: e.stack }; } })()");
            
            console.log("Code evaluation result:", result);
            
            complete({
                success: true,
                code: code,
                result: result,
                type: typeof result
            });
        } catch (evalError) {
            console.error("Error during code evaluation:", evalError);
            
            complete({
                success: false,
                code: code,
                error: evalError.message,
                stack: evalError.stack
            });
        }
    } catch (error) {
        console.error("Error in debug_js function:", error);
        complete({
            success: false,
            error: "Function error: " + error.message,
            stack: error.stack
        });
    }
};
